package com.mes.repository;

import com.mes.domain.ProductionOrder;
import com.mes.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, UUID> {
    Optional<ProductionOrder> findByOrderNo(String orderNo);

    Page<ProductionOrder> findByStatus(OrderStatus status, Pageable pageable);
}
