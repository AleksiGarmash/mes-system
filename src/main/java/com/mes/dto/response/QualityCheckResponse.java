package com.mes.dto.response;

import com.mes.domain.enums.QcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QualityCheckResponse(
        UUID id,
        UUID batchId,
        String parameter,
        BigDecimal expectedValue,
        BigDecimal expectedMin,
        BigDecimal expectedMax,
        String expectedText,
        BigDecimal measuredValue,
        String textValue,
        QcResult result,
        String labTechnician,
        Instant checkedAt
) {}
