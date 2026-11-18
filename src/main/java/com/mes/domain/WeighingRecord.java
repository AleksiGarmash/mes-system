package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weighing_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeighingRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mixing_session_id", nullable = false)
    private MixingSession mixingSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_item_id")
    private RecipeItem recipeItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_batch_id")
    private RawMaterialBatch rawMaterialBatch;

    @Column(name = "target_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal targetQty;

    @Column(name = "actual_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualQty;

    @Column(name = "deviation", precision = 12, scale = 3)
    private BigDecimal deviation;

    @Column(name = "within_tolerance", nullable = false)
    private boolean withinTolerance;

    @Column(name = "weighed_at")
    private Instant weighedAt;

    /** Снимок номера стадии и фактических параметров перемешивания с бланка. */
    @Column(name = "stage_no")
    private Integer stageNo;

    @Column(name = "mix_speed_actual")
    private Integer mixSpeedActual;

    @Column(name = "mix_minutes_actual")
    private Integer mixMinutesActual;

    @PrePersist
    void onCreate() {
        if (weighedAt == null) weighedAt = Instant.now();
        if (targetQty != null && actualQty != null) {
            deviation = actualQty.subtract(targetQty);
        }
    }
}
