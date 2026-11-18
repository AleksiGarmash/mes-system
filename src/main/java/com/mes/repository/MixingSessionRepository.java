package com.mes.repository;

import com.mes.domain.MixingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MixingSessionRepository extends JpaRepository<MixingSession, UUID> {
    List<MixingSession> findByBatchId(UUID batchId);
}
