package com.mes.dto.response;

import com.mes.domain.enums.BatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String batchNo,
        UUID productionOrderId,
        String orderNo,
        BigDecimal qty,
        BatchStatus status,
        String operator,
        Instant producedAt
) {}
