package com.mes.service;

import com.mes.domain.Batch;
import com.mes.domain.QualityCheck;
import com.mes.domain.enums.BatchStatus;
import com.mes.domain.enums.QcResult;
import com.mes.dto.request.QualityCheckRequest;
import com.mes.event.MesEvent;
import com.mes.event.MesEventPublisher;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.BatchRepository;
import com.mes.repository.QualityCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QualityControlService {

    private final QualityCheckRepository qualityCheckRepository;
    private final BatchRepository batchRepository;
    private final MesEventPublisher eventPublisher;

    @Transactional
    public QualityCheck recordCheck(UUID batchId, QualityCheckRequest request) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Партия не найдена: " + batchId));

        QcResult result = evaluate(request);

        QualityCheck check = QualityCheck.builder()
                .batch(batch)
                .parameter(request.parameter())
                .expectedValue(request.expectedValue())
                .measuredValue(request.measuredValue())
                .result(result)
                .labTechnician(request.labTechnician())
                .build();

        qualityCheckRepository.save(check);

        updateBatchStatus(batch);

        return check;
    }

    private QcResult evaluate(QualityCheckRequest request) {
        if (request.expectedValue() == null) {
            return QcResult.PASS;
        }
        // Простая проверка совпадения — в реальной системе тут будут спецификации допусков по параметру
        return request.measuredValue().compareTo(request.expectedValue()) == 0
                ? QcResult.PASS
                : QcResult.FAIL;
    }

    /**
     * Пересчитывает статус партии по совокупности всех QualityCheck-записей и публикует
     * соответствующее событие. Публичный метод — переиспользуется {@code QcChecklistService}
     * после массового создания проверок из подтверждённого QC-чек-листа, чтобы не дублировать
     * эту логику (REJECTED если хоть одна FAIL, RELEASED если все завершены и ни одной FAIL).
     */
    @Transactional
    public void updateBatchStatus(Batch batch) {
        List<QualityCheck> checks = qualityCheckRepository.findByBatchId(batch.getId());
        boolean anyFail = checks.stream().anyMatch(c -> c.getResult() == QcResult.FAIL);
        boolean allDone = checks.stream().noneMatch(c -> c.getResult() == QcResult.PENDING);

        if (anyFail) {
            batch.setStatus(BatchStatus.REJECTED);
            batchRepository.save(batch);
            List<String> failedParams = checks.stream()
                    .filter(c -> c.getResult() == QcResult.FAIL)
                    .map(QualityCheck::getParameter)
                    .collect(Collectors.toList());
            eventPublisher.publishQualityEvent(batch.getId().toString(),
                    new MesEvent.BatchRejected(batch.getId(), batch.getBatchNo(), failedParams, Instant.now()));
        } else if (allDone && !checks.isEmpty()) {
            batch.setStatus(BatchStatus.RELEASED);
            batchRepository.save(batch);
            eventPublisher.publishQualityEvent(batch.getId().toString(),
                    new MesEvent.BatchReleased(batch.getId(), batch.getBatchNo(),
                            batch.getProductionOrder().getRecipe().getCode(), Instant.now()));
        } else {
            batchRepository.save(batch);
        }
    }

    @Transactional(readOnly = true)
    public List<QualityCheck> findByBatch(UUID batchId) {
        return qualityCheckRepository.findByBatchId(batchId);
    }
}
