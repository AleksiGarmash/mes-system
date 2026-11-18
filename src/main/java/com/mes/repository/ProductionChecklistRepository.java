package com.mes.repository;

import com.mes.domain.ProductionChecklist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductionChecklistRepository extends JpaRepository<ProductionChecklist, UUID> {
    List<ProductionChecklist> findByProductionOrderId(UUID productionOrderId);

    Page<ProductionChecklist> findByProductionOrderId(UUID productionOrderId, Pageable pageable);

    Page<ProductionChecklist> findByStatus(com.mes.domain.enums.ChecklistStatus status, Pageable pageable);

    /** Для обнаружения повторной загрузки того же файла в другой чек-лист. */
    java.util.Optional<ProductionChecklist> findFirstByFileSha256(String fileSha256);
}
