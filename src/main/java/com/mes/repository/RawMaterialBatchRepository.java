package com.mes.repository;

import com.mes.domain.RawMaterialBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RawMaterialBatchRepository extends JpaRepository<RawMaterialBatch, UUID> {
    Optional<RawMaterialBatch> findByLotNo(String lotNo);

    java.util.List<RawMaterialBatch> findByMaterialId(UUID materialId);

    Page<RawMaterialBatch> findByMaterialCodeContainingIgnoreCase(String materialCode, Pageable pageable);
}
