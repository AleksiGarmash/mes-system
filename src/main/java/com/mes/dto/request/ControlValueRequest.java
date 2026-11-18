package com.mes.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Фактический замер в контрольной точке рецептуры (pH, плотность), переписанный с бланка. */
public record ControlValueRequest(
        @NotNull UUID controlPointId,
        @NotNull BigDecimal measuredValue
) {}
