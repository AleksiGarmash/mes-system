package com.mes.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RangeValidatorTest {

    private final RangeValidator validator = new RangeValidator();

    @Test
    void withinBothBounds_passes() {
        assertTrue(validator.isWithinRange(BigDecimal.valueOf(4.2), BigDecimal.valueOf(3.5), BigDecimal.valueOf(4.5)));
    }

    @Test
    void belowMin_fails() {
        assertFalse(validator.isWithinRange(BigDecimal.valueOf(3.0), BigDecimal.valueOf(3.5), BigDecimal.valueOf(4.5)));
    }

    @Test
    void aboveMax_fails() {
        assertFalse(validator.isWithinRange(BigDecimal.valueOf(5.0), BigDecimal.valueOf(3.5), BigDecimal.valueOf(4.5)));
    }

    @Test
    void exactlyOnBoundary_passes() {
        assertTrue(validator.isWithinRange(BigDecimal.valueOf(3.5), BigDecimal.valueOf(3.5), BigDecimal.valueOf(4.5)));
        assertTrue(validator.isWithinRange(BigDecimal.valueOf(4.5), BigDecimal.valueOf(3.5), BigDecimal.valueOf(4.5)));
    }

    @Test
    void onlyMinSet_noUpperLimit() {
        assertTrue(validator.isWithinRange(BigDecimal.valueOf(1000), BigDecimal.valueOf(10), null));
    }

    @Test
    void onlyMaxSet_noLowerLimit() {
        assertTrue(validator.isWithinRange(BigDecimal.valueOf(-1000), null, BigDecimal.valueOf(10)));
    }

    @Test
    void nullValue_fails() {
        assertFalse(validator.isWithinRange(null, BigDecimal.ZERO, BigDecimal.TEN));
    }

    @Test
    void allowedValue_matchesIgnoringCaseAndSpaces() {
        assertTrue(validator.isAllowedValue("  Однородная   ЖИДКОСТЬ ", "однородная жидкость; без осадка"));
    }

    @Test
    void allowedValue_notInList_fails() {
        assertFalse(validator.isAllowedValue("мутная", "однородная жидкость;без осадка"));
    }

    @Test
    void allowedValue_blankOrNull_fails() {
        assertFalse(validator.isAllowedValue(null, "а;б"));
        assertFalse(validator.isAllowedValue("  ", "а;б"));
        assertFalse(validator.isAllowedValue("а", null));
    }
}
