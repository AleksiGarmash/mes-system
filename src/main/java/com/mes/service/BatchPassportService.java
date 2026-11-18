package com.mes.service;

import com.mes.domain.Batch;
import com.mes.domain.QualityCheck;
import com.mes.domain.WeighingRecord;
import com.mes.domain.enums.QcResult;
import com.mes.dto.response.BatchPassportResponse;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.BatchRepository;
import com.mes.repository.MixingSessionRepository;
import com.mes.repository.QualityCheckRepository;
import com.mes.repository.WeighingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchPassportService {

    private final BatchRepository batchRepository;
    private final MixingSessionRepository mixingSessionRepository;
    private final WeighingRecordRepository weighingRecordRepository;
    private final QualityCheckRepository qualityCheckRepository;

    @Transactional(readOnly = true)
    public BatchPassportResponse generate(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Партия не найдена: " + batchId));

        List<String> lots = mixingSessionRepository.findByBatchId(batchId).stream()
                .flatMap(s -> weighingRecordRepository.findByMixingSessionId(s.getId()).stream())
                .map(WeighingRecord::getRawMaterialBatch)
                .filter(java.util.Objects::nonNull) // у продуктов без колонки «партия» лота может не быть
                .map(rmb -> rmb.getLotNo())
                .distinct()
                .collect(Collectors.toList());

        List<QualityCheck> checks = qualityCheckRepository.findByBatchId(batchId);
        String qcResult = checks.stream().anyMatch(c -> c.getResult() == QcResult.FAIL)
                ? "FAIL"
                : (checks.isEmpty() ? "PENDING" : "PASS");

        String mixer = mixingSessionRepository.findByBatchId(batchId).stream()
                .findFirst()
                .map(s -> s.getMixerId().toString())
                .orElse("N/A");

        return new BatchPassportResponse(
                batch.getBatchNo(),
                batch.getProductionOrder().getRecipe().getCode(),
                batch.getProductionOrder().getRecipe().getVersion(),
                batch.getProducedAt(),
                mixer,
                batch.getOperator(),
                lots,
                qcResult
        );
    }
}
