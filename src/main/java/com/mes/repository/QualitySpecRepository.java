package com.mes.repository;

import com.mes.domain.QualitySpec;
import com.mes.domain.enums.QualitySpecStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualitySpecRepository extends JpaRepository<QualitySpec, UUID> {
    List<QualitySpec> findByCodeOrderByVersionDesc(String code);
    Optional<QualitySpec> findByCodeAndStatus(String code, QualitySpecStatus status);
}
