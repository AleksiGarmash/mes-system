package com.mes.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record WeighRequest(
        @NotNull UUID recipeItemId,
        @NotNull UUID rawMaterialBatchId,
        @NotNull BigDecimal actualQty
) {}
