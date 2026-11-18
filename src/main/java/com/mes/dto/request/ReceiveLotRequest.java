package com.mes.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Приёмка лота сырья на склад. */
public record ReceiveLotRequest(
        @NotNull UUID materialId,
        String materialCode,
        @NotBlank String lotNo,
        String supplier,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        LocalDate expiry
) {}
