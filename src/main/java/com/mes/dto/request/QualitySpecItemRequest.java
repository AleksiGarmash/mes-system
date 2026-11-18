package com.mes.dto.request;

import com.mes.domain.enums.QcParameterType;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Параметр QC-спецификации. NUMERIC — задаются minValue/maxValue; QUALITATIVE — allowedValues
 * (допустимые значения через «;»). Если parameterType не указан, считается NUMERIC.
 */
public record QualitySpecItemRequest(
        String parameterCode,
        @NotBlank String parameterName,
        BigDecimal minValue,
        BigDecimal maxValue,
        String unit,
        Integer sequenceNo,
        QcParameterType parameterType,
        String allowedValues
) {
    public QualitySpecItemRequest(String parameterCode, String parameterName, BigDecimal minValue,
                                  BigDecimal maxValue, String unit, Integer sequenceNo) {
        this(parameterCode, parameterName, minValue, maxValue, unit, sequenceNo, null, null);
    }
}
