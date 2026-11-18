package com.mes.service;

import com.mes.exception.DosageOutOfToleranceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Проверка фактической навески компонента на соответствие допускам рецептуры.
 * min = target - toleranceMinus (%), max = target + tolerancePlus (%)
 */
@Component
public class DosageValidator {

    public boolean isWithinTolerance(BigDecimal target, BigDecimal actual,
                                      BigDecimal tolerancePlusPct, BigDecimal toleranceMinusPct) {
        BigDecimal minusAbs = target.multiply(toleranceMinusPct).divide(BigDecimal.valueOf(100));
        BigDecimal plusAbs = target.multiply(tolerancePlusPct).divide(BigDecimal.valueOf(100));

        BigDecimal min = target.subtract(minusAbs);
        BigDecimal max = target.add(plusAbs);

        return actual.compareTo(min) >= 0 && actual.compareTo(max) <= 0;
    }

    public void validateWeighing(BigDecimal target, BigDecimal actual,
                                  BigDecimal tolerancePlusPct, BigDecimal toleranceMinusPct) {
        if (!isWithinTolerance(target, actual, tolerancePlusPct, toleranceMinusPct)) {
            throw new DosageOutOfToleranceException(
                    "Отклонение дозировки: target=%s, actual=%s, +%s%%/-%s%%"
                            .formatted(target, actual, tolerancePlusPct, toleranceMinusPct));
        }
    }
}
