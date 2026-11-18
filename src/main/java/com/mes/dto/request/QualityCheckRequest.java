package com.mes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QualityCheckRequest(
        @NotBlank String parameter,
        BigDecimal expectedValue,
        @NotNull BigDecimal measuredValue,
        String labTechnician
) {}
