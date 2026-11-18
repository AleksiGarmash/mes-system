package com.mes.dto.response;

import com.mes.domain.enums.ControlPointKind;
import com.mes.domain.enums.RecipeStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecipeResponse(
        UUID id,
        String code,
        String name,
        Integer version,
        RecipeStatus status,
        String qualitySpecCode,
        Boolean requireRawMaterialLot,
        List<RecipeStageResponse> stages,
        List<RecipeItemResponse> items,
        List<RecipeControlPointResponse> controlPoints
) {
    public record RecipeStageResponse(
            UUID id,
            Integer stageNo,
            String name,
            BigDecimal plannedQty
    ) {}

    public record RecipeItemResponse(
            UUID id,
            UUID materialId,
            String materialCode,
            BigDecimal targetQty,
            String unit,
            BigDecimal tolerancePlus,
            BigDecimal toleranceMinus,
            Integer sequenceNo,
            Integer stageNo,
            Integer sourceStageNo,
            String mixSpeedNorm,
            Integer mixMinutesNorm,
            String note
    ) {}

    public record RecipeControlPointResponse(
            UUID id,
            Integer stageNo,
            ControlPointKind kind,
            String parameterCode,
            String parameterName,
            BigDecimal minValue,
            BigDecimal maxValue,
            String unit,
            Integer sequenceNo
    ) {}
}
