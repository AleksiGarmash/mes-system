package com.mes.service;

import com.mes.analytics.SpcCalculator;
import com.mes.analytics.SpcPoint;
import com.mes.analytics.SpcReport;
import com.mes.analytics.SpcResult;
import com.mes.domain.ProcessControlRecord;
import com.mes.domain.QualityCheck;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.ProcessControlRecordRepository;
import com.mes.repository.QualityCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SPC поверх реальных данных: результаты QC-протоколов или замеры контрольных точек процесса
 * по рецептуре. Границы допуска берутся из снимка самой свежей записи (допуск мог меняться
 * между версиями спецификации, но для оценки текущей воспроизводимости важен действующий).
 */
@Service
@RequiredArgsConstructor
public class SpcService {

    public enum Source { QC, PROCESS }

    private final QualityCheckRepository qualityCheckRepository;
    private final ProcessControlRecordRepository processControlRecordRepository;
    private final SpcCalculator spcCalculator;

    @Transactional(readOnly = true)
    public SpcResult analyze(String recipeCode, String parameter, Source source, Instant from) {
        Instant since = from != null ? from : Instant.EPOCH;

        List<SpcPoint> points = new ArrayList<>();
        BigDecimal lsl = null;
        BigDecimal usl = null;

        if (source == Source.QC) {
            for (QualityCheck check : qualityCheckRepository.findNumericSeries(recipeCode, parameter, since)) {
                lsl = check.getExpectedMin();
                usl = check.getExpectedMax();
                points.add(point(check.getCheckedAt(), check.getBatch().getBatchNo(), check.getMeasuredValue(),
                        check.getExpectedMin(), check.getExpectedMax()));
            }
        } else {
            for (ProcessControlRecord rec : processControlRecordRepository.findSeries(recipeCode, parameter, since)) {
                lsl = rec.getExpectedMin();
                usl = rec.getExpectedMax();
                points.add(point(rec.getChecklist().getConfirmedAt(),
                        rec.getChecklist().getBatchId() != null ? rec.getChecklist().getBatchId().toString() : null,
                        rec.getMeasuredValue(), rec.getExpectedMin(), rec.getExpectedMax()));
            }
        }

        if (points.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Нет числовых измерений параметра «%s» для рецептуры %s.".formatted(parameter, recipeCode));
        }

        Double lslValue = lsl != null ? lsl.doubleValue() : null;
        Double uslValue = usl != null ? usl.doubleValue() : null;
        SpcReport report = spcCalculator.analyze(points.stream().map(SpcPoint::value).toList(), lslValue, uslValue);

        return new SpcResult(recipeCode, parameter, source.name(), lslValue, uslValue, points, report);
    }

    private SpcPoint point(Instant at, String label, BigDecimal value, BigDecimal min, BigDecimal max) {
        boolean outOfSpec = (min != null && value.compareTo(min) < 0) || (max != null && value.compareTo(max) > 0);
        return new SpcPoint(at, label, value.doubleValue(), outOfSpec);
    }
}
