package com.mes.dto.response;

import com.mes.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNo,
        UUID recipeId,
        String recipeCode,
        Integer recipeVersion,
        BigDecimal plannedQty,
        BigDecimal actualQty,
        OrderStatus status,
        UUID mixerId,
        Instant plannedDate
) {}
