package com.mes.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Потребность в материале: для расчёта выдачи по FEFO и для самой выдачи. */
public record StockQuantityRequest(
        @NotNull UUID materialId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity
) {}
