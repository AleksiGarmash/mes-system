package com.mes.dto.response;

import com.mes.stock.FefoAllocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AllocationResponse(List<PickResponse> picks, BigDecimal shortage, boolean complete) {

    public record PickResponse(UUID lotId, String lotNo, LocalDate expiry, BigDecimal quantity) {
    }

    public static AllocationResponse from(FefoAllocation allocation) {
        return new AllocationResponse(
                allocation.picks().stream()
                        .map(p -> new PickResponse(p.lot().id(), p.lot().lotNo(), p.lot().expiry(), p.quantity()))
                        .toList(),
                allocation.shortage(),
                allocation.isComplete());
    }
}
