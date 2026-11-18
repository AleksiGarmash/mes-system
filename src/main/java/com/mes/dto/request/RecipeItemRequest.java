package com.mes.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Строка рецептуры. {@code stageNo} — номер стадии (если у рецептуры есть стадии),
 * {@code sourceStageNo} — компонент является результатом более ранней стадии.
 */
public record RecipeItemRequest(
        @NotNull UUID materialId,
        String materialCode,
        @NotNull @DecimalMin("0.0") BigDecimal targetQty,
        String unit,
        @NotNull @DecimalMin("0.0") BigDecimal tolerancePlus,
        @NotNull @DecimalMin("0.0") BigDecimal toleranceMinus,
        Integer sequenceNo,
        Integer stageNo,
        Integer sourceStageNo,
        String mixSpeedNorm,
        Integer mixMinutesNorm,
        String note
) {
    /** Совместимость с одностадийными рецептурами без параметров перемешивания. */
    public RecipeItemRequest(UUID materialId, String materialCode, BigDecimal targetQty, String unit,
                             BigDecimal tolerancePlus, BigDecimal toleranceMinus, Integer sequenceNo) {
        this(materialId, materialCode, targetQty, unit, tolerancePlus, toleranceMinus, sequenceNo,
                null, null, null, null, null);
    }
}
