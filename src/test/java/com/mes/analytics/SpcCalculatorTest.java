package com.mes.analytics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpcCalculatorTest {

    private final SpcCalculator calculator = new SpcCalculator();

    private List<Double> alternating(int n, double a, double b) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(i % 2 == 0 ? a : b);
        }
        return list;
    }

    @Test
    void basicStatistics_matchHandCalculation() {
        // 1,2,3,4,5: среднее 3, выборочное σ = sqrt(10/4), MR = 1 → σw = 1/1.128
        SpcReport r = calculator.analyze(List.of(1.0, 2.0, 3.0, 4.0, 5.0), null, null);

        assertEquals(5, r.n());
        assertEquals(3.0, r.mean(), 1e-9);
        assertEquals(Math.sqrt(2.5), r.stdDev(), 1e-9);
        assertEquals(1.0 / 1.128, r.sigmaWithin(), 1e-9);
        assertEquals(3.0 + 3 / 1.128, r.ucl(), 1e-9);
        assertEquals(3.0 - 3 / 1.128, r.lcl(), 1e-9);
        assertFalse(r.sufficientData());
        assertNull(r.cp());
        assertNull(r.cpk());
    }

    @Test
    void cpAndCpk_forCenteredProcess_areEqualAndMatchFormula() {
        List<Double> data = alternating(30, 9.9, 10.1); // среднее 10.0, MR = 0.2
        SpcReport r = calculator.analyze(data, 9.0, 11.0);

        double sigma = 0.2 / 1.128;
        assertEquals(2.0 / (6 * sigma), r.cp(), 1e-9);
        assertEquals(r.cp(), r.cpk(), 1e-9);
        assertTrue(r.sufficientData());
    }

    @Test
    void cpk_isLowerThanCp_whenProcessIsOffCenter() {
        List<Double> data = alternating(30, 10.4, 10.6); // среднее 10.5, допуск 9..11
        SpcReport r = calculator.analyze(data, 9.0, 11.0);

        assertTrue(r.cpk() < r.cp());
        double sigma = 0.2 / 1.128;
        assertEquals((11.0 - 10.5) / (3 * sigma), r.cpk(), 1e-9);
    }

    @Test
    void oneSidedLimit_givesCpkButNoCp() {
        SpcReport r = calculator.analyze(alternating(30, 9.9, 10.1), null, 11.0);

        assertNull(r.cp());
        assertNotNull(r.cpk());
        assertTrue(r.cpk() > 0);
    }

    @Test
    void ppk_usesOverallStandardDeviation() {
        SpcReport r = calculator.analyze(alternating(30, 9.9, 10.1), 9.0, 11.0);

        double expected = (11.0 - r.mean()) / (3 * r.stdDev());
        assertEquals(expected, r.ppk(), 1e-9);
    }

    @Test
    void constantSeries_hasZeroSigmaNoCapabilityAndNoSignals() {
        SpcReport r = calculator.analyze(List.of(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0), 4.0, 6.0);

        assertEquals(0.0, r.sigmaWithin(), 0);
        assertNull(r.cp());
        assertNull(r.cpk());
        assertTrue(r.signals().isEmpty());
    }

    @Test
    void stableProcess_hasNoSignals() {
        SpcReport r = calculator.analyze(alternating(30, 9.9, 10.1), 9.0, 11.0);

        assertTrue(r.signals().isEmpty(), r.signals().toString());
    }

    @Test
    void singleOutlier_triggersWE1() {
        List<Double> data = alternating(30, 10.0, 10.2);
        data.add(11.0);

        SpcReport r = calculator.analyze(data, null, null);

        assertTrue(r.signals().stream().anyMatch(s -> s.rule().equals("WE1") && s.index() == 30), r.signals().toString());
    }

    @Test
    void meanShift_triggersWE4_afterEightPointsOnOneSide() {
        List<Double> data = alternating(20, 9.9, 10.1);
        for (int i = 0; i < 8; i++) {
            data.add(10.15 + (i % 2) * 0.02); // сдвиг вверх
        }

        SpcReport r = calculator.analyze(data, null, null);

        assertTrue(r.signals().stream().anyMatch(s -> s.rule().equals("WE4")), r.signals().toString());
    }

    @Test
    void emptyInput_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.analyze(List.of(), null, null));
    }

    @Test
    void singlePoint_doesNotCrash() {
        SpcReport r = calculator.analyze(List.of(7.0), 5.0, 9.0);

        assertEquals(1, r.n());
        assertEquals(0.0, r.stdDev(), 0);
        assertTrue(r.signals().isEmpty());
        assertFalse(r.sufficientData());
    }
}
