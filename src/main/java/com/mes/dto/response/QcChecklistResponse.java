package com.mes.dto.response;

import com.mes.domain.enums.QcChecklistStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QcChecklistResponse(
        UUID id,
        UUID batchId,
        String batchNo,
        UUID qualitySpecId,
        QcChecklistStatus status,
        String imagePath,
        Double ocrConfidence,
        Instant uploadedAt,
        Boolean qrVerified,
        Integer pageCount,
        String decisionNote,
        String nonconformityActions,
        String secondSignatory,
        List<QcChecklistItemResponse> items
) {
    public record QcChecklistItemResponse(
            UUID id,
            String parameterNameRaw,
            UUID qualitySpecItemId,
            BigDecimal measuredValue,
            String textValue,
            Double confidence,
            boolean needsReview,
            String reviewNote,
            Boolean withinRange,
            boolean correctedByOperator
    ) {}
}
