package com.mes.repository;

import com.mes.domain.Batch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {
    Optional<Batch> findByBatchNo(String batchNo);

    Page<Batch> findByStatus(com.mes.domain.enums.BatchStatus status, Pageable pageable);

    /** Следующее значение последовательности номеров партий. */
    @Query(value = "SELECT nextval('batch_no_seq')", nativeQuery = true)
    long nextBatchSequence();
}
