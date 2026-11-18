package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Стадия рецептуры (например, «приготовление модифицирующего раствора» и «приготовление
 * готового продукта»). Масса стадии используется для проверки суммы навесок: сумма фактических
 * масс компонентов стадии должна совпадать с плановой массой с небольшим допуском на округление.
 */
@Entity
@Table(name = "recipe_stages", uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_id", "stage_no"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeStage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "stage_no", nullable = false)
    private Integer stageNo;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "planned_qty", precision = 12, scale = 3)
    private BigDecimal plannedQty;
}
