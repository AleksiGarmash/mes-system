package com.mes.domain;

import com.mes.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "production_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "planned_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal plannedQty;

    @Column(name = "actual_qty", precision = 12, scale = 3)
    private BigDecimal actualQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "mixer_id")
    private UUID mixerId;

    @Column(name = "planned_date")
    private Instant plannedDate;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = OrderStatus.PLANNED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
