package com.mes.domain;

import com.mes.domain.enums.ControlPointKind;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Контрольная точка производственного процесса: замер pH, плотности и т.п. по ходу стадии
 * или на готовом продукте. Допуски процесса задаются здесь и независимы от QC-спецификации:
 * внутрипроизводственный допуск обычно уже спецификационного.
 */
@Entity
@Table(name = "recipe_control_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeControlPoint {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    /** Номер стадии, к которой относится замер; {@code null} для контроля готового продукта. */
    @Column(name = "stage_no")
    private Integer stageNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ControlPointKind kind;

    @Column(name = "parameter_code", length = 100)
    private String parameterCode;

    @Column(name = "parameter_name", nullable = false, length = 255)
    private String parameterName;

    @Column(name = "min_value", precision = 12, scale = 3)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 12, scale = 3)
    private BigDecimal maxValue;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "sequence_no")
    private Integer sequenceNo;
}
