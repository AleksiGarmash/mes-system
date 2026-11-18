package com.mes.repository;

import com.mes.domain.QualitySpecItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QualitySpecItemRepository extends JpaRepository<QualitySpecItem, UUID> {
}
