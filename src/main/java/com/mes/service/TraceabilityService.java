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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Прослеживаемость в обе стороны.
 * <ul>
 *   <li>Назад: из чего сделана партия ({@link #genealogy(String)}).</li>
 *   <li>Вперёд: в каких партиях использован лот сырья ({@link #recallReport(String)}) — отзыв.</li>
 *   <li>Действие: перевод затронутых партий на карантин ({@link #quarantineByRawLot(String, String)}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TraceabilityService {

    static final String AUDIT_BATCH = "BATCH";
    private static final Set<BatchStatus> QUARANTINE_ELIGIBLE =
            EnumSet.of(BatchStatus.IN_PRODUCTION, BatchStatus.PENDING_QC, BatchStatus.RELEASED);

    private final RawMaterialBatchRepository rawMaterialBatchRepository;
    private final WeighingRecordRepository weighingRecordRepository;
    private final BatchRepository batchRepository;
    private final MixingSessionRepository mixingSessionRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final AuditService auditService;
    private final MesEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public RecallReport recallReport(String lotNo) {
        RawMaterialBatch lot = findLot(lotNo);
        Map<UUID, Batch> batches = new LinkedHashMap<>();
        Map<UUID, BigDecimal> usedQty = new HashMap<>();

        for (WeighingRecord record : weighingRecordRepository.findByRawMaterialBatchId(lot.getId())) {
            Batch batch = record.getMixingSession().getBatch();
            batches.putIfAbsent(batch.getId(), batch);
            usedQty.merge(batch.getId(), record.getActualQty(), BigDecimal::add);
        }

        List<RecallReport.AffectedBatch> affected = batches.values().stream()
                .sorted(Comparator.comparing(Batch::getProducedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(b -> new RecallReport.AffectedBatch(
                        b.getBatchNo(), b.getStatus(), b.getProducedAt(),
                        b.getProductionOrder().getOrderNo(),
                        b.getProductionOrder().getRecipe().getCode(),
                        usedQty.get(b.getId())))
                .toList();

        return new RecallReport(lot.getLotNo(), lot.getMaterialCode(), lot.getSupplier(), lot.getExpiry(), affected);
    }

    /**
     * Переводит на карантин все затронутые лотом партии, которые ещё не отбракованы и не на карантине.
     * Каждое изменение пишется в журнал аудита и публикуется событием {@link MesEvent.BatchQuarantined}.
     */
    @Transactional
    public QuarantineResult quarantineByRawLot(String lotNo, String reason) {
        RawMaterialBatch lot = findLot(lotNo);
        String why = reason != null && !reason.isBlank() ? reason : "Отзыв лота сырья " + lot.getLotNo();

        Map<UUID, Batch> batches = new LinkedHashMap<>();
        for (WeighingRecord record : weighingRecordRepository.findByRawMaterialBatchId(lot.getId())) {
            Batch batch = record.getMixingSession().getBatch();
            batches.putIfAbsent(batch.getId(), batch);
        }

        List<String> quarantined = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Batch batch : batches.values()) {
            if (!QUARANTINE_ELIGIBLE.contains(batch.getStatus())) {
                skipped.add(batch.getBatchNo());
                continue;
            }
            BatchStatus before = batch.getStatus();
            batch.setStatus(BatchStatus.QUARANTINE);
            batchRepository.save(batch);
            auditService.recordStatus(AUDIT_BATCH, batch.getId(), before, BatchStatus.QUARANTINE);
            eventPublisher.publishBatchEvent(batch.getId().toString(),
                    new MesEvent.BatchQuarantined(batch.getId(), batch.getBatchNo(), why, Instant.now()));
            quarantined.add(batch.getBatchNo());
        }
        return new QuarantineResult(lot.getLotNo(), quarantined, skipped);
    }

    @Transactional(readOnly = true)
    public BatchGenealogy genealogy(String batchNo) {
        Batch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new ResourceNotFoundException("Партия не найдена: " + batchNo));

        List<BatchGenealogy.ComponentUsage> components = new ArrayList<>();
        for (MixingSession session : mixingSessionRepository.findByBatchId(batch.getId())) {
            for (WeighingRecord w : weighingRecordRepository.findByMixingSessionId(session.getId())) {
                RawMaterialBatch lot = w.getRawMaterialBatch();
                RecipeItem item = w.getRecipeItem();
                components.add(new BatchGenealogy.ComponentUsage(
                        item != null ? item.getMaterialCode() : (lot != null ? lot.getMaterialCode() : null),
                        w.getStageNo(),
                        lot != null ? lot.getLotNo() : null,
                        lot != null ? lot.getSupplier() : null,
                        lot != null ? lot.getExpiry() : null,
                        w.getTargetQty(), w.getActualQty(), w.isWithinTolerance(),
                        w.getMixSpeedActual(), w.getMixMinutesActual()));
            }
        }

        List<BatchGenealogy.QualityResultRef> checks = qualityCheckRepository.findByBatchId(batch.getId()).stream()
                .map(q -> new BatchGenealogy.QualityResultRef(q.getParameter(), q.getResult(), q.getMeasuredValue(),
                        q.getTextValue(), q.getExpectedMin(), q.getExpectedMax(), q.getExpectedText()))
                .toList();

        Recipe recipe = batch.getProductionOrder().getRecipe();
        return new BatchGenealogy(batch.getBatchNo(), batch.getStatus(), batch.getProductionOrder().getOrderNo(),
                recipe.getCode(), recipe.getVersion(), batch.getProducedAt(), components, checks);
    }

    private RawMaterialBatch findLot(String lotNo) {
        return rawMaterialBatchRepository.findByLotNo(lotNo)
                .orElseThrow(() -> new ResourceNotFoundException("Партия сырья не найдена: " + lotNo));
    }
}
