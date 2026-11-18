package com.mes.analytics;

import java.util.List;

/**
 * Результат статистического анализа ряда измерений.
 *
 * @param n               число точек
 * @param mean            среднее
 * @param stdDev          общее выборочное стандартное отклонение (для Pp/Ppk)
 * @param sigmaWithin     внутригрупповой сигма по скользящему размаху (MR̄ / 1,128), для карты и Cp/Cpk
 * @param centerLine      центральная линия карты (= среднее)
 * @param ucl             верхняя контрольная граница (среднее + 3σ)
 * @param lcl             нижняя контрольная граница (среднее − 3σ)
 * @param cp              потенциальная воспроизводимость (нужны обе границы допуска), иначе {@code null}
 * @param cpk             фактическая воспроизводимость (достаточно одной границы), иначе {@code null}
 * @param ppk             Ppk по общему стандартному отклонению, иначе {@code null}
 * @param sufficientData  достаточно ли данных для надёжных выводов (не менее 20 точек)
 * @param signals         сработавшие правила нестабильности
 */
public record SpcReport(
        int n,
        double mean,
        double stdDev,
        double sigmaWithin,
        double centerLine,
        double ucl,
        double lcl,
        Double cp,
        Double cpk,
        Double ppk,
        boolean sufficientData,
        List<SpcSignal> signals
) {
}
