package com.mes.dto.response;

import java.time.Instant;
import java.util.List;

public record BatchPassportResponse(
        String batchNo,
        String recipeCode,
        Integer recipeVersion,
        Instant producedAt,
        String mixer,
        String operator,
        List<String> rawMaterialLots,
        String qcResult
) {}
