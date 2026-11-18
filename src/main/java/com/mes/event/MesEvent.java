package com.mes.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * События MES, публикуемые в Kafka для интеграции с ERP/1С, складом и BI.
 */
public sealed interface MesEvent permits
        MesEvent.BatchStarted,
        MesEvent.BatchReleased,
        MesEvent.BatchRejected,
        MesEvent.DosageOutOfTolerance,
        MesEvent.BatchQuarantined,
        MesEvent.RecipeActivated {

    record BatchStarted(UUID batchId, String batchNo, UUID orderId, Instant occurredAt) implements MesEvent {}

    /** Партия переведена на карантин (например, при отзыве лота сырья). */
    record BatchQuarantined(UUID batchId, String batchNo, String reason, Instant occurredAt) implements MesEvent {}

    record BatchReleased(UUID batchId, String batchNo, String recipeCode, Instant occurredAt) implements MesEvent {}

    record BatchRejected(UUID batchId, String batchNo, List<String> failedParameters, Instant occurredAt) implements MesEvent {}

    record DosageOutOfTolerance(UUID batchId, UUID recipeItemId, BigDecimal target, BigDecimal actual,
                                 Instant occurredAt) implements MesEvent {}

    record RecipeActivated(UUID recipeId, String code, Integer version, Instant occurredAt) implements MesEvent {}
}
