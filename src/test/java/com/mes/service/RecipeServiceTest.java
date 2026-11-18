package com.mes.service;

import com.mes.domain.Recipe;
import com.mes.domain.enums.ControlPointKind;
import com.mes.dto.request.*;
import com.mes.event.MesEventPublisher;
import com.mes.exception.InvalidRequestException;
import com.mes.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock private RecipeRepository recipeRepository;
    @Mock private MesEventPublisher eventPublisher;

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(recipeRepository, eventPublisher);
        lenient().when(recipeRepository.findByCodeOrderByVersionDesc(any())).thenReturn(List.of());
        lenient().when(recipeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RecipeItemRequest item(String code, Integer stageNo, Integer sourceStageNo) {
        return new RecipeItemRequest(UUID.randomUUID(), code, BigDecimal.TEN, "kg",
                new BigDecimal("2"), BigDecimal.ZERO, 1, stageNo, sourceStageNo, null, null, null);
    }

    private CreateRecipeRequest request(List<RecipeItemRequest> items, List<RecipeStageRequest> stages,
                                        List<RecipeControlPointRequest> points) {
        return new CreateRecipeRequest("R-1", "Тестовая смесь", items, "QC-1", false, stages, points);
    }

    @Test
    void create_twoStageRecipe_linksItemsToStagesAndKeepsFlags() {
        List<RecipeStageRequest> stages = List.of(
                new RecipeStageRequest(1, "Раствор", BigDecimal.valueOf(300)),
                new RecipeStageRequest(2, "Продукт", BigDecimal.valueOf(1000)));
        List<RecipeItemRequest> items = List.of(
                item("Компонент А", 1, null),
                item("Раствор", 2, 1));
        List<RecipeControlPointRequest> points = List.of(new RecipeControlPointRequest(
                null, ControlPointKind.FINISHED_PRODUCT, "ph", "pH", new BigDecimal("10.0"), new BigDecimal("10.5"), null, 1));

        Recipe recipe = recipeService.create(request(items, stages, points));

        assertEquals(2, recipe.getStages().size());
        assertEquals(2, recipe.getItems().size());
        assertEquals(1, recipe.getItems().get(0).getStage().getStageNo());
        assertEquals(1, recipe.getItems().get(1).getSourceStageNo());
        assertEquals(1, recipe.getControlPoints().size());
        assertFalse(recipe.getRequireRawMaterialLot());
        assertEquals("QC-1", recipe.getQualitySpecCode());
    }

    @Test
    void create_itemWithoutStage_whenStagesDefined_isRejected() {
        List<RecipeStageRequest> stages = List.of(new RecipeStageRequest(1, "Стадия", null));
        assertThrows(InvalidRequestException.class,
                () -> recipeService.create(request(List.of(item("A", null, null)), stages, null)));
    }

    @Test
    void create_itemReferencesUnknownStage_isRejected() {
        List<RecipeStageRequest> stages = List.of(new RecipeStageRequest(1, "Стадия", null));
        assertThrows(InvalidRequestException.class,
                () -> recipeService.create(request(List.of(item("A", 5, null)), stages, null)));
    }

    @Test
    void create_sourceStageNotEarlier_isRejected() {
        List<RecipeStageRequest> stages = List.of(
                new RecipeStageRequest(1, "Раствор", null), new RecipeStageRequest(2, "Продукт", null));
        assertThrows(InvalidRequestException.class,
                () -> recipeService.create(request(List.of(item("A", 1, 2)), stages, null)));
    }

    @Test
    void create_duplicateStageNumbers_isRejected() {
        List<RecipeStageRequest> stages = List.of(
                new RecipeStageRequest(1, "А", null), new RecipeStageRequest(1, "Б", null));
        assertThrows(InvalidRequestException.class,
                () -> recipeService.create(request(List.of(item("A", 1, null)), stages, null)));
    }

    @Test
    void create_controlPointWithoutRange_isRejected() {
        List<RecipeControlPointRequest> points = List.of(new RecipeControlPointRequest(
                null, ControlPointKind.PROCESS, null, "pH", null, null, null, 1));
        assertThrows(InvalidRequestException.class,
                () -> recipeService.create(request(List.of(item("A", null, null)), null, points)));
    }

    @Test
    void create_simpleRecipeWithoutStages_stillWorks() {
        Recipe recipe = recipeService.create(new CreateRecipeRequest("R-2", "Простая", List.of(item("A", null, null))));

        assertTrue(recipe.getStages().isEmpty());
        assertNull(recipe.getItems().get(0).getStage());
        assertTrue(recipe.getRequireRawMaterialLot());
    }
}
