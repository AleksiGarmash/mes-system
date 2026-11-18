package com.mes.analytics;

import java.util.List;

/**
 * Ответ анализа SPC: ряд точек, границы допуска (из снимка последней записи) и статистика.
 *
 * @param source QC (протоколы контроля качества) или PROCESS (контрольные точки процесса)
 */
public record SpcResult(
        String recipeCode,
        String parameter,
        String source,
        Double lsl,
        Double usl,
        List<SpcPoint> points,
        SpcReport report
) {
}
