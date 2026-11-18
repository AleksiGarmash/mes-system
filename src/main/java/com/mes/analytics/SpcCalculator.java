package com.mes.analytics;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Статистический контроль процесса (SPC) для карты индивидуальных значений (I-MR).
 * <ul>
 *   <li>σ внутри процесса оценивается по среднему скользящему размаху: σ = MR̄ / d2, d2 = 1,128;</li>
 *   <li>Cp = (USL − LSL) / 6σ; Cpk = min(USL − μ, μ − LSL) / 3σ (допускается одна граница);</li>
 *   <li>Ppk считается так же по общему выборочному стандартному отклонению;</li>
 *   <li>правила нестабильности Western Electric: WE1 — точка за 3σ; WE2 — 2 из 3 подряд за 2σ
 *       по одну сторону; WE3 — 4 из 5 подряд за 1σ по одну сторону; WE4 — 8 подряд по одну сторону
 *       от центральной линии.</li>
 * </ul>
 * Это сигналы для расследования, а не доказательство причины: при малом числе точек
 * ({@link #MIN_RELIABLE_POINTS}) отчёт помечается как недостаточный.
 */
@Component
public class SpcCalculator {

    static final double D2_FOR_MOVING_RANGE_2 = 1.128;
    public static final int MIN_RELIABLE_POINTS = 20;

    public SpcReport analyze(List<Double> values, Double lsl, Double usl) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Нет данных для анализа");
        }
        int n = values.size();
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = sampleStdDev(values, mean);
        double sigmaWithin = withinSigma(values);

        double ucl = mean + 3 * sigmaWithin;
        double lcl = mean - 3 * sigmaWithin;

        Double cp = null;
        if (lsl != null && usl != null && sigmaWithin > 0) {
            cp = (usl - lsl) / (6 * sigmaWithin);
        }
        Double cpk = capability(mean, sigmaWithin, lsl, usl);
        Double ppk = capability(mean, stdDev, lsl, usl);

        List<SpcSignal> signals = sigmaWithin > 0 ? detectSignals(values, mean, sigmaWithin) : List.of();

        return new SpcReport(n, mean, stdDev, sigmaWithin, mean, ucl, lcl, cp, cpk, ppk,
                n >= MIN_RELIABLE_POINTS, signals);
    }

    private double sampleStdDev(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0;
        }
        double sumSq = 0;
        for (double v : values) {
            sumSq += (v - mean) * (v - mean);
        }
        return Math.sqrt(sumSq / (values.size() - 1));
    }

    private double withinSigma(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double sumRange = 0;
        for (int i = 1; i < values.size(); i++) {
            sumRange += Math.abs(values.get(i) - values.get(i - 1));
        }
        double meanRange = sumRange / (values.size() - 1);
        return meanRange / D2_FOR_MOVING_RANGE_2;
    }

    private Double capability(double mean, double sigma, Double lsl, Double usl) {
        if (sigma <= 0 || (lsl == null && usl == null)) {
            return null;
        }
        double upper = usl != null ? (usl - mean) / (3 * sigma) : Double.POSITIVE_INFINITY;
        double lower = lsl != null ? (mean - lsl) / (3 * sigma) : Double.POSITIVE_INFINITY;
        return Math.min(upper, lower);
    }

    private List<SpcSignal> detectSignals(List<Double> values, double mean, double sigma) {
        List<SpcSignal> signals = new ArrayList<>();
        int n = values.size();

        for (int i = 0; i < n; i++) {
            double z = (values.get(i) - mean) / sigma;

            if (Math.abs(z) > 3) {
                signals.add(new SpcSignal("WE1", i, "Точка за границами 3σ"));
            }
            if (i >= 2 && countBeyond(values, i - 2, i, mean, sigma, 2) >= 2 && sameSideAsLast(values, i - 2, i, mean, 2, sigma)) {
                signals.add(new SpcSignal("WE2", i, "2 из 3 последних точек за 2σ по одну сторону"));
            }
            if (i >= 4 && countBeyond(values, i - 4, i, mean, sigma, 1) >= 4 && sameSideAsLast(values, i - 4, i, mean, 1, sigma)) {
                signals.add(new SpcSignal("WE3", i, "4 из 5 последних точек за 1σ по одну сторону"));
            }
            if (i >= 7 && allSameSide(values, i - 7, i, mean)) {
                signals.add(new SpcSignal("WE4", i, "8 последних точек по одну сторону от центральной линии (сдвиг среднего)"));
            }
        }
        return signals;
    }

    /** Число точек окна [from, to], отклонившихся от среднего более чем на k·σ в сторону последней точки. */
    private int countBeyond(List<Double> v, int from, int to, double mean, double sigma, int k) {
        double lastSide = Math.signum(v.get(to) - mean);
        int count = 0;
        for (int i = from; i <= to; i++) {
            double d = v.get(i) - mean;
            if (Math.signum(d) == lastSide && Math.abs(d) > k * sigma) {
                count++;
            }
        }
        return count;
    }

    /** Сигнал имеет смысл, только если последняя точка окна сама лежит за порогом k·σ. */
    private boolean sameSideAsLast(List<Double> v, int from, int to, double mean, int k, double sigma) {
        return Math.abs(v.get(to) - mean) > k * sigma;
    }

    private boolean allSameSide(List<Double> v, int from, int to, double mean) {
        double side = Math.signum(v.get(from) - mean);
        if (side == 0) {
            return false;
        }
        for (int i = from; i <= to; i++) {
            if (Math.signum(v.get(i) - mean) != side) {
                return false;
            }
        }
        return true;
    }
}
