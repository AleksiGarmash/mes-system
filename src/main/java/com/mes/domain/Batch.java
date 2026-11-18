package com.mes.domain;

import com.mes.domain.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 50)
    private String batchNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    @Column(name = "qty", precision = 12, scale = 3)
    private BigDecimal qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "operator", length = 255)
    private String operator;

    @Column(name = "produced_at")
    private Instant producedAt;

    @Column(name = "passport_url", length = 500)
    private String passportUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) status = BatchStatus.IN_PRODUCTION;
    }
}
