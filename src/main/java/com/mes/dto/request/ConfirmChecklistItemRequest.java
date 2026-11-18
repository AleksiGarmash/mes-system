package com.mes.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Корректировка одной строки чек-листа оператором перед подтверждением
 * (используется, если OCR не распознал поле или распознал с ошибкой).
 */
public record ConfirmChecklistItemRequest(
        @NotNull UUID checklistItemId,
        UUID materialId,
        UUID rawMaterialBatchId,
        BigDecimal weight,
        Integer mixSpeedActual,
        Integer mixMinutesActual,
        String note
) {
    public ConfirmChecklistItemRequest(UUID checklistItemId, UUID materialId, UUID rawMaterialBatchId, BigDecimal weight) {
        this(checklistItemId, materialId, rawMaterialBatchId, weight, null, null, null);
    }
}
