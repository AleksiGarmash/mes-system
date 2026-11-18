package com.mes.dto.response;

import com.mes.domain.enums.BatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Отчёт об отзыве: в каких партиях продукции использован лот сырья. */
public record RecallReport(
        String lotNo,
        String materialCode,
        String supplier,
        LocalDate expiry,
        List<AffectedBatch> affectedBatches
) {
    public record AffectedBatch(
            String batchNo,
            BatchStatus status,
            Instant producedAt,
            String orderNo,
            String recipeCode,
            BigDecimal usedQty
    ) {}
}
