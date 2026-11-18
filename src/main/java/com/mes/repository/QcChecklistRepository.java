package com.mes.repository;

import com.mes.domain.QcChecklist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QcChecklistRepository extends JpaRepository<QcChecklist, UUID> {
    Optional<QcChecklist> findByBatchId(UUID batchId);

    Page<QcChecklist> findByStatus(com.mes.domain.enums.QcChecklistStatus status, Pageable pageable);

    Optional<QcChecklist> findFirstByFileSha256(String fileSha256);
}
