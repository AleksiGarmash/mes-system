package com.mes.service;

import com.mes.domain.*;
import com.mes.domain.enums.BatchStatus;
import com.mes.domain.enums.MixingSessionStatus;
import com.mes.dto.request.WeighRequest;
import com.mes.event.MesEvent;
import com.mes.event.MesEventPublisher;
import com.mes.exception.DosageOutOfToleranceException;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MixingService {

    private final MixingSessionRepository mixingSessionRepository;
    private final WeighingRecordRepository weighingRecordRepository;
    private final BatchRepository batchRepository;
    private final RawMaterialBatchRepository rawMaterialBatchRepository;
    private final DosageValidator dosageValidator;
    private final MesEventPublisher eventPublisher;

    /** Находит или создаёт активную сессию смешивания для партии. */
    @Transactional
    public MixingSession getOrCreateSession(UUID batchId, UUID mixerId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Партия не найдена: " + batchId));

        return mixingSessionRepository.findByBatchId(batchId).stream()
                .filter(s -> s.getStatus() != MixingSessionStatus.FINISHED
                        && s.getStatus() != MixingSessionStatus.ABORTED)
                .findFirst()
                .orElseGet(() -> mixingSessionRepository.save(
                        MixingSession.builder()
                                .batch(batch)
                                .mixerId(mixerId)
                                .status(MixingSessionStatus.WEIGHING)
                                .build()));
    }

    @Transactional
    public WeighingRecord weigh(UUID mixingSessionId, WeighRequest request, RecipeItem recipeItem) {
        MixingSession session = mixingSessionRepository.findById(mixingSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Сессия смешивания не найдена: " + mixingSessionId));

        RawMaterialBatch rmBatch = rawMaterialBatchRepository.findById(request.rawMaterialBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Партия сырья не найдена: " + request.rawMaterialBatchId()));

        boolean withinTolerance = dosageValidator.isWithinTolerance(
                recipeItem.getTargetQty(), request.actualQty(),
                recipeItem.getTolerancePlus(), recipeItem.getToleranceMinus());

        if (!withinTolerance) {
            eventPublisher.publishBatchEvent(
                    session.getBatch().getId().toString(),
                    new MesEvent.DosageOutOfTolerance(
                            session.getBatch().getId(), recipeItem.getId(),
                            recipeItem.getTargetQty(), request.actualQty(), Instant.now()));
        }

        // Жёсткая проверка: выбрасывает исключение, если отклонение недопустимо
        dosageValidator.validateWeighing(
                recipeItem.getTargetQty(), request.actualQty(),
                recipeItem.getTolerancePlus(), recipeItem.getToleranceMinus());

        WeighingRecord record = WeighingRecord.builder()
                .mixingSession(session)
                .recipeItem(recipeItem)
                .rawMaterialBatch(rmBatch)
                .targetQty(recipeItem.getTargetQty())
                .actualQty(request.actualQty())
                .withinTolerance(withinTolerance)
                .build();

        return weighingRecordRepository.save(record);
    }

    @Transactional
    public MixingSession finish(UUID mixingSessionId) {
        MixingSession session = mixingSessionRepository.findById(mixingSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Сессия смешивания не найдена: " + mixingSessionId));

        session.setStatus(MixingSessionStatus.FINISHED);
        session.setFinishedAt(java.time.Instant.now());
        mixingSessionRepository.save(session);

        Batch batch = session.getBatch();
        batch.setStatus(BatchStatus.PENDING_QC);
        batch.setProducedAt(java.time.Instant.now());
        batchRepository.save(batch);

        return session;
    }
}
