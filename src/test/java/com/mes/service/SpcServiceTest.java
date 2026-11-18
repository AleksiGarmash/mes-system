package com.mes.service;

import com.mes.analytics.SpcCalculator;
import com.mes.analytics.SpcResult;
import com.mes.domain.Batch;
import com.mes.domain.ProcessControlRecord;
import com.mes.domain.ProductionChecklist;
import com.mes.domain.QualityCheck;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.ProcessControlRecordRepository;
import com.mes.repository.QualityCheckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SpcServiceTest {

    private QualityCheckRepository qcRepository;
    private ProcessControlRecordRepository processRepository;
    private SpcService service;

    @BeforeEach
    void setUp() {
        qcRepository = Mockito.mock(QualityCheckRepository.class);
        processRepository = Mockito.mock(ProcessControlRecordRepository.class);
        service = new SpcService(qcRepository, processRepository, new SpcCalculator());
    }

    private List<QualityCheck> qcSeries(int n, double base) {
        List<QualityCheck> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(QualityCheck.builder()
                    .batch(Batch.builder().batchNo("B-" + i).build())
                    .parameter("pH")
                    .measuredValue(BigDecimal.valueOf(base + (i % 2 == 0 ? -0.1 : 0.1)))
                    .expectedMin(new BigDecimal("9.0"))
                    .expectedMax(new BigDecimal("11.0"))
                    .checkedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 3600L))
                    .build());
        }
        return list;
    }

    @Test
    void analyze_qc_buildsPointsLimitsAndStatistics() {
        when(qcRepository.findNumericSeries(any(), any(), any())).thenReturn(qcSeries(25, 10.0));

        SpcResult result = service.analyze("R-1", "pH", SpcService.Source.QC, null);

        assertEquals(25, result.points().size());
        assertEquals(9.0, result.lsl());
        assertEquals(11.0, result.usl());
        assertEquals("B-0", result.points().get(0).label());
        assertEquals(10.0, result.report().mean(), 0.01);
        assertNotNull(result.report().cpk());
        assertTrue(result.report().sufficientData());
        assertTrue(result.points().stream().noneMatch(p -> p.outOfSpec()));
    }

    @Test
    void analyze_marksPointsOutsideSpecLimits() {
        List<QualityCheck> series = qcSeries(5, 10.0);
        series.get(2).setMeasuredValue(new BigDecimal("11.4"));
        when(qcRepository.findNumericSeries(any(), any(), any())).thenReturn(series);

        SpcResult result = service.analyze("R-1", "pH", SpcService.Source.QC, null);

        assertTrue(result.points().get(2).outOfSpec());
        assertFalse(result.points().get(1).outOfSpec());
    }

    @Test
    void analyze_process_usesControlRecords() {
        ProductionChecklist checklist = ProductionChecklist.builder()
                .confirmedAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        ProcessControlRecord rec = ProcessControlRecord.builder()
                .checklist(checklist).measuredValue(new BigDecimal("10.2"))
                .expectedMin(new BigDecimal("10.0")).expectedMax(new BigDecimal("10.5")).build();
        when(processRepository.findSeries(any(), any(), any())).thenReturn(List.of(rec));

        SpcResult result = service.analyze("R-1", "pH готового продукта", SpcService.Source.PROCESS, null);

        assertEquals("PROCESS", result.source());
        assertEquals(1, result.points().size());
        assertFalse(result.report().sufficientData());
    }

    @Test
    void analyze_noMeasurements_isNotFound() {
        when(qcRepository.findNumericSeries(any(), any(), any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.analyze("R-1", "pH", SpcService.Source.QC, null));
    }
}
