package com.mes.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Корректировка одной строки протокола QC лаборантом перед подтверждением
 * (используется, если OCR не распознал параметр или значение, или распознал с ошибкой).
 * {@code textValue} — для качественных параметров («внешний вид»).
 */
public record ConfirmQcChecklistItemRequest(
        @NotNull UUID checklistItemId,
        UUID qualitySpecItemId,
        BigDecimal measuredValue,
        String textValue
) {
    public ConfirmQcChecklistItemRequest(UUID checklistItemId, UUID qualitySpecItemId, BigDecimal measuredValue) {
        this(checklistItemId, qualitySpecItemId, measuredValue, null);
    }
}
