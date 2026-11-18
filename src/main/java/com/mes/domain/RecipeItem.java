package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "material_code", length = 50)
    private String materialCode;

    @Column(name = "target_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal targetQty;

    @Column(name = "unit", length = 10)
    private String unit;

    @Column(name = "tolerance_plus", nullable = false, precision = 5, scale = 2)
    private BigDecimal tolerancePlus;

    @Column(name = "tolerance_minus", nullable = false, precision = 5, scale = 2)
    private BigDecimal toleranceMinus;

    @Column(name = "sequence_no")
    private Integer sequenceNo;

    /** Стадия рецептуры, к которой относится компонент ({@code null} для одностадийных рецептур). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private RecipeStage stage;

    /**
     * Если задан, компонент — это результат более ранней стадии (промежуточный продукт), а не сырьё:
     * например, «модифицирующий раствор» из стадии 1 в составе стадии 2.
     */
    @Column(name = "source_stage_no")
    private Integer sourceStageNo;

    /** Норма оборотов вала как на бланке («не более 120», «150-180») — текстом, это ориентир, а не допуск. */
    @Column(name = "mix_speed_norm", length = 50)
    private String mixSpeedNorm;

    @Column(name = "mix_minutes_norm")
    private Integer mixMinutesNorm;

    @Column(name = "note", length = 500)
    private String note;
}
