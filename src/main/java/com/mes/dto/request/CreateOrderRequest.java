package com.mes.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateOrderRequest(
        @NotBlank String orderNo,
        @NotNull UUID recipeId,
        @NotNull @DecimalMin("0.0") BigDecimal plannedQty,
        UUID mixerId,
        Instant plannedDate
) {}
