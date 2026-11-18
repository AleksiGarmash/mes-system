package com.mes.dto.response;

import com.mes.domain.RawMaterialBatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LotResponse(
        UUID id,
        String lotNo,
        UUID materialId,
        String materialCode,
        String supplier,
        BigDecimal quantityAvailable,
        LocalDate expiry
) {
    public static LotResponse from(RawMaterialBatch lot) {
        return new LotResponse(lot.getId(), lot.getLotNo(), lot.getMaterialId(), lot.getMaterialCode(),
                lot.getSupplier(), lot.getQuantityAvailable(), lot.getExpiry());
    }
}
