package com.mes.service;

import com.mes.domain.Batch;
import com.mes.domain.ProductionOrder;
import com.mes.domain.Recipe;
import com.mes.domain.enums.BatchStatus;
import com.mes.domain.enums.OrderStatus;
import com.mes.dto.request.CreateOrderRequest;
import com.mes.event.MesEvent;
import com.mes.event.MesEventPublisher;
import com.mes.exception.InvalidStateTransitionException;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.BatchRepository;
import com.mes.repository.ProductionOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ProductionOrderService {

    private final ProductionOrderRepository orderRepository;
    private final BatchRepository batchRepository;
    private final RecipeService recipeService;
    private final MesEventPublisher eventPublisher;
    private final BatchNumberService batchNumberService;

    @Transactional
    public ProductionOrder create(CreateOrderRequest request) {
        Recipe recipe = recipeService.getById(request.recipeId());

        ProductionOrder order = ProductionOrder.builder()
                .orderNo(request.orderNo())
                .recipe(recipe)
                .plannedQty(request.plannedQty())
                .mixerId(request.mixerId())
                .plannedDate(request.plannedDate())
                .status(OrderStatus.PLANNED)
                .build();

        return orderRepository.save(order);
    }

    @Transactional
    public Batch start(UUID orderId) {
        ProductionOrder order = getById(orderId);

        if (order.getStatus() != OrderStatus.PLANNED) {
            throw new InvalidStateTransitionException(
                    "Задание должно быть в статусе PLANNED, текущий статус: " + order.getStatus());
        }

        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);

        String batchNo = batchNumberService.next();

        Batch batch = Batch.builder()
                .batchNo(batchNo)
                .productionOrder(order)
                .status(BatchStatus.IN_PRODUCTION)
                .build();

        Batch saved = batchRepository.save(batch);

        eventPublisher.publishBatchEvent(saved.getId().toString(),
                new MesEvent.BatchStarted(saved.getId(), saved.getBatchNo(), order.getId(), Instant.now()));

        return saved;
    }

    @Transactional
    public ProductionOrder complete(UUID orderId) {
        ProductionOrder order = getById(orderId);
        order.setStatus(OrderStatus.COMPLETED);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public ProductionOrder getById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Производственное задание не найдено: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ProductionOrder> list(OrderStatus status, Pageable pageable) {
        return status != null ? orderRepository.findByStatus(status, pageable) : orderRepository.findAll(pageable);
    }
}
