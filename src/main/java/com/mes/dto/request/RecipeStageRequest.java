package com.mes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecipeStageRequest(
        @NotNull Integer stageNo,
        @NotBlank String name,
        BigDecimal plannedQty
) {}
