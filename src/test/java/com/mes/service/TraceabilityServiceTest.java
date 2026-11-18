package com.mes.service;

import com.mes.domain.*;
import com.mes.domain.enums.BatchStatus;
import com.mes.dto.response.BatchGenealogy;
import com.mes.dto.response.QuarantineResult;
import com.mes.dto.response.RecallReport;
import com.mes.event.MesEvent;
import com.mes.event.MesEventPublisher;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TraceabilityServiceTest {

    private RawMaterialBatchRepository lotRepository;
    private WeighingRecordRepository weighingRepository;
    private BatchRepository batchRepository;
    private MixingSessionRepository sessionRepository;
    private QualityCheckRepository qcRepository;
    private AuditService auditService;
    private MesEventPublisher eventPublisher;
    private TraceabilityService service;

    private RawMaterialBatch lot;
    private ProductionOrder order;

    @BeforeEach
    void setUp() {
        lotRepository = Mockito.mock(RawMaterialBatchRepository.class);
        weighingRepository = Mockito.mock(WeighingRecordRepository.class);
        batchRepository = Mockito.mock(BatchRepository.class);
        sessionRepository = Mockito.mock(MixingSessionRepository.class);
        qcRepository = Mockito.mock(QualityCheckRepository.class);
        auditService = Mockito.mock(AuditService.class);
        eventPublisher = Mockito.mock(MesEventPublisher.class);
        service = new TraceabilityService(lotRepository, weighingRepository, batchRepository, sessionRepository,
                qcRepository, auditService, eventPublisher);

        lot = RawMaterialBatch.builder().id(UUID.randomUUID()).lotNo("RM-1").materialCode("Песок")
                .supplier("ООО Карьер").expiry(LocalDate.of(2027, 1, 1)).build();
        Recipe recipe = Recipe.builder().id(UUID.randomUUID()).code("SM-17").version(2).build();
        order = ProductionOrder.builder().id(UUID.randomUUID()).orderNo("ORD-1").recipe(recipe).build();
        when(lotRepository.findByLotNo("RM-1")).thenReturn(Optional.of(lot));
    }

    private Batch batch(String no, BatchStatus status, String producedAt) {
        return Batch.builder().id(UUID.randomUUID()).batchNo(no).status(status)
                .productionOrder(order).producedAt(Instant.parse(producedAt)).build();
    }

    private WeighingRecord use(Batch batch, double qty) {
        MixingSession session = MixingSession.builder().id(UUID.randomUUID()).batch(batch).build();
        return WeighingRecord.builder().id(UUID.randomUUID()).mixingSession(session).rawMaterialBatch(lot)
                .targetQty(BigDecimal.valueOf(qty)).actualQty(BigDecimal.valueOf(qty)).withinTolerance(true).build();
    }

    @Test
    void recallReport_groupsByBatchSumsQuantityAndSortsByProductionTime() {
        Batch later = batch("B-2", BatchStatus.RELEASED, "2026-03-02T10:00:00Z");
        Batch earlier = batch("B-1", BatchStatus.PENDING_QC, "2026-03-01T10:00:00Z");
        // две навески одного лота в одной партии: суммируются
        when(weighingRepository.findByRawMaterialBatchId(lot.getId())).thenReturn(
                List.of(use(later, 100), use(earlier, 50), use(earlier, 25)));

        RecallReport report = service.recallReport("RM-1");

        assertEquals("RM-1", report.lotNo());
        assertEquals("ООО Карьер", report.supplier());
        assertEquals(2, report.affectedBatches().size());
        assertEquals("B-1", report.affectedBatches().get(0).batchNo());
        assertEquals(0, new BigDecimal("75").compareTo(report.affectedBatches().get(0).usedQty()));
        assertEquals("B-2", report.affectedBatches().get(1).batchNo());
        assertEquals("SM-17", report.affectedBatches().get(1).recipeCode());
    }

    @Test
    void recallReport_lotNeverUsed_hasNoAffectedBatches() {
        when(weighingRepository.findByRawMaterialBatchId(lot.getId())).thenReturn(List.of());

        assertTrue(service.recallReport("RM-1").affectedBatches().isEmpty());
    }

    @Test
    void recallReport_unknownLot_isNotFound() {
        when(lotRepository.findByLotNo("NOPE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.recallReport("NOPE"));
    }

    @Test
    void quarantine_movesOnlyEligibleBatchesAndPublishesEventsAndAudit() {
        Batch released = batch("B-1", BatchStatus.RELEASED, "2026-03-01T10:00:00Z");
        Batch pending = batch("B-2", BatchStatus.PENDING_QC, "2026-03-02T10:00:00Z");
        Batch rejected = batch("B-3", BatchStatus.REJECTED, "2026-03-03T10:00:00Z");
        Batch already = batch("B-4", BatchStatus.QUARANTINE, "2026-03-04T10:00:00Z");
        when(weighingRepository.findByRawMaterialBatchId(lot.getId())).thenReturn(
                List.of(use(released, 10), use(pending, 10), use(rejected, 10), use(already, 10)));

        QuarantineResult result = service.quarantineByRawLot("RM-1", "Загрязнение у поставщика");

        assertEquals(List.of("B-1", "B-2"), result.quarantinedBatchNos());
        assertEquals(List.of("B-3", "B-4"), result.skippedBatchNos());
        assertEquals(BatchStatus.QUARANTINE, released.getStatus());
        assertEquals(BatchStatus.QUARANTINE, pending.getStatus());
        assertEquals(BatchStatus.REJECTED, rejected.getStatus());

        verify(batchRepository, times(2)).save(any());
        verify(auditService).recordStatus("BATCH", released.getId(), BatchStatus.RELEASED, BatchStatus.QUARANTINE);
        verify(auditService).recordStatus("BATCH", pending.getId(), BatchStatus.PENDING_QC, BatchStatus.QUARANTINE);

        ArgumentCaptor<MesEvent> events = ArgumentCaptor.forClass(MesEvent.class);
        verify(eventPublisher, times(2)).publishBatchEvent(any(), events.capture());
        MesEvent.BatchQuarantined first = (MesEvent.BatchQuarantined) events.getAllValues().get(0);
        assertEquals("Загрязнение у поставщика", first.reason());
    }

    @Test
    void quarantine_withoutReason_usesDefaultMentioningTheLot() {
        Batch released = batch("B-1", BatchStatus.RELEASED, "2026-03-01T10:00:00Z");
        when(weighingRepository.findByRawMaterialBatchId(lot.getId())).thenReturn(List.of(use(released, 10)));

        service.quarantineByRawLot("RM-1", null);

        ArgumentCaptor<MesEvent> events = ArgumentCaptor.forClass(MesEvent.class);
        verify(eventPublisher).publishBatchEvent(any(), events.capture());
        assertTrue(((MesEvent.BatchQuarantined) events.getValue()).reason().contains("RM-1"));
    }

    @Test
    void quarantine_nothingToDo_publishesNothing() {
        when(weighingRepository.findByRawMaterialBatchId(lot.getId())).thenReturn(List.of());

        QuarantineResult result = service.quarantineByRawLot("RM-1", null);

        assertTrue(result.quarantinedBatchNos().isEmpty());
        verify(eventPublisher, never()).publishBatchEvent(any(), any());
    }

    @Test
    void genealogy_listsComponentsWithLotsAndQualityResults() {
        Batch b = batch("B-9", BatchStatus.RELEASED, "2026-03-01T10:00:00Z");
        MixingSession session = MixingSession.builder().id(UUID.randomUUID()).batch(b).build();
        RecipeItem item = RecipeItem.builder().id(UUID.randomUUID()).materialCode("Песок").build();
        WeighingRecord w = WeighingRecord.builder().id(UUID.randomUUID()).mixingSession(session)
                .recipeItem(item).rawMaterialBatch(lot).stageNo(1)
                .targetQty(BigDecimal.valueOf(750)).actualQty(BigDecimal.valueOf(749)).withinTolerance(true).build();
        QualityCheck q = QualityCheck.builder().parameter("pH").result(com.mes.domain.enums.QcResult.PASS)
                .measuredValue(new BigDecimal("10.2")).expectedMin(BigDecimal.TEN).expectedMax(new BigDecimal("11")).build();
        when(batchRepository.findByBatchNo("B-9")).thenReturn(Optional.of(b));
        when(sessionRepository.findByBatchId(b.getId())).thenReturn(List.of(session));
        when(weighingRepository.findByMixingSessionId(session.getId())).thenReturn(List.of(w));
        when(qcRepository.findByBatchId(b.getId())).thenReturn(List.of(q));

        BatchGenealogy g = service.genealogy("B-9");

        assertEquals("SM-17", g.recipeCode());
        assertEquals(2, g.recipeVersion());
        assertEquals(1, g.components().size());
        assertEquals("Песок", g.components().get(0).materialCode());
        assertEquals("RM-1", g.components().get(0).lotNo());
        assertEquals("ООО Карьер", g.components().get(0).supplier());
        assertEquals(1, g.qualityChecks().size());
        assertEquals("pH", g.qualityChecks().get(0).parameter());
    }

    @Test
    void genealogy_componentWithoutLot_isStillListed() {
        Batch b = batch("B-9", BatchStatus.RELEASED, "2026-03-01T10:00:00Z");
        MixingSession session = MixingSession.builder().id(UUID.randomUUID()).batch(b).build();
        WeighingRecord w = WeighingRecord.builder().id(UUID.randomUUID()).mixingSession(session)
                .targetQty(BigDecimal.TEN).actualQty(BigDecimal.TEN).build();
        when(batchRepository.findByBatchNo("B-9")).thenReturn(Optional.of(b));
        when(sessionRepository.findByBatchId(b.getId())).thenReturn(List.of(session));
        when(weighingRepository.findByMixingSessionId(session.getId())).thenReturn(List.of(w));
        when(qcRepository.findByBatchId(b.getId())).thenReturn(List.of());

        BatchGenealogy g = service.genealogy("B-9");

        assertEquals(1, g.components().size());
        assertNull(g.components().get(0).lotNo());
    }

    @Test
    void genealogy_unknownBatch_isNotFound() {
        when(batchRepository.findByBatchNo("X")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.genealogy("X"));
    }
}
