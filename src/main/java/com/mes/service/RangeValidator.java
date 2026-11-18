package com.mes.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Проверка измеренного значения параметра качества на попадание в диапазон
 * допуска QC-спецификации. В отличие от {@link DosageValidator} (допуск в процентах
 * от цели), здесь границы заданы напрямую как абсолютные {@code min}/{@code max} —
 * у параметров качества нет единой "целевой" величины, только приемлемый коридор.
 * Отсутствующая граница ({@code null}) означает "без ограничения с этой стороны"
 * (например, только верхний предел для содержания вредных примесей).
 */
@Component
public class RangeValidator {

    public boolean isWithinRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return false;
        }
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        if (max != null && value.compareTo(max) > 0) {
            return false;
        }
        return true;
    }

    /**
     * Проверка качественного параметра: значение должно совпасть (без учёта регистра и лишних
     * пробелов) с одним из допустимых значений, перечисленных через «;».
     */
    public boolean isAllowedValue(String value, String allowedValues) {
        if (value == null || value.isBlank() || allowedValues == null || allowedValues.isBlank()) {
            return false;
        }
        String normalized = normalize(value);
        for (String allowed : allowedValues.split(";")) {
            if (!allowed.isBlank() && normalize(allowed).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }
}
