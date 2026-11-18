package com.mes.dto.response;

import com.mes.domain.QcChecklist;
import com.mes.domain.QcChecklistItem;
import com.mes.domain.enums.QcChecklistStatus;

import java.time.Instant;
import java.util.UUID;

public record QcChecklistSummary(
        UUID id,
        UUID batchId,
        String batchNo,
        QcChecklistStatus status,
        Instant uploadedAt,
        int itemCount,
        int needsReviewCount,
        Instant createdAt
) {
    public static QcChecklistSummary from(QcChecklist c) {
        int needsReview = (int) c.getItems().stream().filter(QcChecklistItem::isNeedsReview).count();
        return new QcChecklistSummary(c.getId(), c.getBatch().getId(), c.getBatch().getBatchNo(), c.getStatus(),
                c.getUploadedAt(), c.getItems().size(), needsReview, c.getCreatedAt());
    }
}
