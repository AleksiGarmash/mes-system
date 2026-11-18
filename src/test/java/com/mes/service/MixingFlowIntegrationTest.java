package com.mes.service;

import com.mes.domain.Batch;
import com.mes.domain.ProductionOrder;
import com.mes.domain.Recipe;
import com.mes.domain.RawMaterialBatch;
import com.mes.dto.request.CreateOrderRequest;
import com.mes.dto.request.CreateRecipeRequest;
import com.mes.dto.request.RecipeItemRequest;
import com.mes.dto.request.WeighRequest;
import com.mes.exception.DosageOutOfToleranceException;
import com.mes.repository.RawMaterialBatchRepository;
import com.mes.repository.RecipeItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional // тест выполняется в транзакции и откатывается: ленивые связи доступны, база остаётся чистой
class MixingFlowIntegrationTest {

    /** Keycloak в тесте не нужен: проверка JWT подменена, остальная конфигурация безопасности настоящая. */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mes")
            .withUsername("mes")
            .withPassword("mes");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private ProductionOrderService productionOrderService;

    @Autowired
    private MixingService mixingService;

    @Autowired
    private RawMaterialBatchRepository rawMaterialBatchRepository;

    @Autowired
    private RecipeItemRepository recipeItemRepository;

    @Test
    void fullFlow_recipeToWeighing_withinTolerance_succeeds() {
        // 1. Create and activate a recipe with one component (target 100kg, ±3%)
        RecipeItemRequest itemReq = new RecipeItemRequest(
                UUID.randomUUID(), "SAND-01", BigDecimal.valueOf(100), "kg",
                BigDecimal.valueOf(3), BigDecimal.valueOf(3), 1);
        Recipe recipe = recipeService.create(new CreateRecipeRequest("R-TEST", "Test Mix", List.of(itemReq)));
        recipe = recipeService.activate(recipe.getId());
        final UUID recipeId = recipe.getId();

        // 2. Create a production order against it
        ProductionOrder order = productionOrderService.create(
                new CreateOrderRequest("ORD-TEST-001", recipe.getId(), BigDecimal.valueOf(1000), null, null));

        // 3. Start the order -> creates a batch
        Batch batch = productionOrderService.start(order.getId());
        assertNotNull(batch.getBatchNo());

        // 4. Register a raw material lot
        RawMaterialBatch rmBatch = rawMaterialBatchRepository.save(
                RawMaterialBatch.builder()
                        .lotNo("RM-LOT-001")
                        .materialId(itemReq.materialId())
                        .materialCode("SAND-01")
                        .quantityAvailable(BigDecimal.valueOf(500))
                        .build());

        // 5. Create a mixing session and weigh within tolerance (102kg vs 100kg target, +3%/-3%)
        var session = mixingService.getOrCreateSession(batch.getId(), UUID.randomUUID());
        var recipeItem = recipeItemRepository.findAll().stream()
                .filter(ri -> ri.getRecipe().getId().equals(recipeId))
                .findFirst()
                .orElseThrow();

        var weighing = mixingService.weigh(session.getId(),
                new WeighRequest(recipeItem.getId(), rmBatch.getId(), BigDecimal.valueOf(102)),
                recipeItem);

        assertTrue(weighing.isWithinTolerance());

        // 6. Finish mixing -> batch should move to PENDING_QC
        var finished = mixingService.finish(session.getId());
        assertEquals(com.mes.domain.enums.MixingSessionStatus.FINISHED, finished.getStatus());
    }

    @Test
    void weighing_outOfTolerance_throwsAndRejects() {
        RecipeItemRequest itemReq = new RecipeItemRequest(
                UUID.randomUUID(), "CLAY-01", BigDecimal.valueOf(50), "kg",
                BigDecimal.valueOf(2), BigDecimal.valueOf(2), 1);
        Recipe recipe = recipeService.create(new CreateRecipeRequest("R-TEST-2", "Test Mix 2", List.of(itemReq)));
        recipe = recipeService.activate(recipe.getId());
        final UUID recipeId = recipe.getId();

        ProductionOrder order = productionOrderService.create(
                new CreateOrderRequest("ORD-TEST-002", recipe.getId(), BigDecimal.valueOf(500), null, null));
        Batch batch = productionOrderService.start(order.getId());

        RawMaterialBatch rmBatch = rawMaterialBatchRepository.save(
                RawMaterialBatch.builder()
                        .lotNo("RM-LOT-002")
                        .materialId(itemReq.materialId())
                        .materialCode("CLAY-01")
                        .quantityAvailable(BigDecimal.valueOf(200))
                        .build());

        var session = mixingService.getOrCreateSession(batch.getId(), UUID.randomUUID());
        var recipeItem = recipeItemRepository.findAll().stream()
                .filter(ri -> ri.getRecipe().getId().equals(recipeId))
                .findFirst()
                .orElseThrow();

        // 60kg vs 50kg target with ±2% tolerance -> way out of range
        assertThrows(DosageOutOfToleranceException.class, () ->
                mixingService.weigh(session.getId(),
                        new WeighRequest(recipeItem.getId(), rmBatch.getId(), BigDecimal.valueOf(60)),
                        recipeItem));
    }
}
