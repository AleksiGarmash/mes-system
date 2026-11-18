package com.mes.service;

import com.mes.exception.DosageOutOfToleranceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DosageValidatorTest {

    private final DosageValidator validator = new DosageValidator();

    @Test
    void withinTolerance_passesValidation() {
        BigDecimal target = BigDecimal.valueOf(100);
        BigDecimal actual = BigDecimal.valueOf(102);
        BigDecimal plus = BigDecimal.valueOf(3);   // +3%
        BigDecimal minus = BigDecimal.valueOf(3);  // -3%

        assertTrue(validator.isWithinTolerance(target, actual, plus, minus));
        assertDoesNotThrow(() -> validator.validateWeighing(target, actual, plus, minus));
    }

    @Test
    void aboveUpperTolerance_throws() {
        BigDecimal target = BigDecimal.valueOf(100);
        BigDecimal actual = BigDecimal.valueOf(110);
        BigDecimal plus = BigDecimal.valueOf(3);
        BigDecimal minus = BigDecimal.valueOf(3);

        assertFalse(validator.isWithinTolerance(target, actual, plus, minus));
        assertThrows(DosageOutOfToleranceException.class,
                () -> validator.validateWeighing(target, actual, plus, minus));
    }

    @Test
    void belowLowerTolerance_throws() {
        BigDecimal target = BigDecimal.valueOf(100);
        BigDecimal actual = BigDecimal.valueOf(90);
        BigDecimal plus = BigDecimal.valueOf(3);
        BigDecimal minus = BigDecimal.valueOf(3);

        assertFalse(validator.isWithinTolerance(target, actual, plus, minus));
        assertThrows(DosageOutOfToleranceException.class,
                () -> validator.validateWeighing(target, actual, plus, minus));
    }

    @Test
    void exactlyOnBoundary_passes() {
        BigDecimal target = BigDecimal.valueOf(100);
        BigDecimal actual = BigDecimal.valueOf(103); // exactly +3%
        BigDecimal plus = BigDecimal.valueOf(3);
        BigDecimal minus = BigDecimal.valueOf(3);

        assertTrue(validator.isWithinTolerance(target, actual, plus, minus));
    }
}
