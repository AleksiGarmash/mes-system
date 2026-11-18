package com.mes.dto.request;

import com.mes.domain.enums.ControlPointKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecipeControlPointRequest(
        Integer stageNo,
        @NotNull ControlPointKind kind,
        String parameterCode,
        @NotBlank String parameterName,
        BigDecimal minValue,
        BigDecimal maxValue,
        String unit,
        Integer sequenceNo
) {}
