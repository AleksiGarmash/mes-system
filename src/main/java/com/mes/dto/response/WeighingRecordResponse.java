package com.mes.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WeighingRecordResponse(
        UUID id,
        UUID mixingSessionId,
        UUID recipeItemId,
        UUID rawMaterialBatchId,
        BigDecimal targetQty,
        BigDecimal actualQty,
        BigDecimal deviation,
        boolean withinTolerance,
        Instant weighedAt,
        Integer stageNo
) {}
