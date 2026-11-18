package com.mes.repository;

import com.mes.domain.WeighingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WeighingRecordRepository extends JpaRepository<WeighingRecord, UUID> {
    List<WeighingRecord> findByMixingSessionId(UUID mixingSessionId);

    /** Все использования партии сырья: основа прослеживаемости «вперёд» (отзыв). */
    List<WeighingRecord> findByRawMaterialBatchId(UUID rawMaterialBatchId);
}
