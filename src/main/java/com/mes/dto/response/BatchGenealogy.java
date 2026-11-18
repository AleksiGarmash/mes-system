package com.mes.dto.response;

import com.mes.domain.enums.BatchStatus;
import com.mes.domain.enums.QcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Полная родословная партии: рецептура, использованные лоты сырья, результаты контроля качества. */
public record BatchGenealogy(
        String batchNo,
        BatchStatus status,
        String orderNo,
        String recipeCode,
        Integer recipeVersion,
        Instant producedAt,
        List<ComponentUsage> components,
        List<QualityResultRef> qualityChecks
) {
    public record ComponentUsage(
            String materialCode,
            Integer stageNo,
            String lotNo,
            String supplier,
            LocalDate expiry,
            BigDecimal targetQty,
            BigDecimal actualQty,
            boolean withinTolerance,
            Integer mixSpeedActual,
            Integer mixMinutesActual
    ) {}

    public record QualityResultRef(
            String parameter,
            QcResult result,
            BigDecimal measuredValue,
            String textValue,
            BigDecimal expectedMin,
            BigDecimal expectedMax,
            String expectedText
    ) {}
}
