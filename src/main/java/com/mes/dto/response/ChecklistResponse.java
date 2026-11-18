package com.mes.dto.response;

import com.mes.domain.enums.ChecklistStatus;
import com.mes.domain.enums.ControlPointKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChecklistResponse(
        UUID id,
        UUID productionOrderId,
        ChecklistStatus status,
        String imagePath,
        Double ocrConfidence,
        Instant uploadedAt,
        UUID batchId,
        Boolean qrVerified,
        Integer pageCount,
        String reviewSummary,
        List<ChecklistItemResponse> items,
        List<ControlRecordResponse> controlRecords
) {
    public record ControlRecordResponse(
            UUID id,
            UUID controlPointId,
            ControlPointKind kind,
            Integer stageNo,
            String parameterName,
            BigDecimal expectedMin,
            BigDecimal expectedMax,
            BigDecimal measuredValue,
            boolean withinRange
    ) {}

    public record ChecklistItemResponse(
            UUID id,
            String materialNameRaw,
            UUID materialId,
            String batchNumberRaw,
            UUID rawMaterialBatchId,
            BigDecimal weight,
            Double confidence,
            boolean needsReview,
            String reviewNote,
            Boolean withinTolerance,
            boolean correctedByOperator,
            UUID recipeItemId,
            Integer mixSpeedActual,
            Integer mixMinutesActual,
            String note
    ) {}
}
