package com.mes.dto.response;

import com.mes.domain.enums.QcParameterType;
import com.mes.domain.enums.QualitySpecStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QualitySpecResponse(
        UUID id,
        String code,
        String name,
        Integer version,
        QualitySpecStatus status,
        List<QualitySpecItemResponse> items
) {
    public record QualitySpecItemResponse(
            UUID id,
            String parameterCode,
            String parameterName,
            BigDecimal minValue,
            BigDecimal maxValue,
            String unit,
            Integer sequenceNo,
            QcParameterType parameterType,
            String allowedValues
    ) {}
}
