package com.mes.repository;

import com.mes.domain.QcChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QcChecklistItemRepository extends JpaRepository<QcChecklistItem, UUID> {
    List<QcChecklistItem> findByChecklistId(UUID checklistId);
}
