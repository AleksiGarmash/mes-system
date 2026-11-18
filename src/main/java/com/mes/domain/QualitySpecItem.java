package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Один параметр QC-спецификации: диапазон допуска задаётся напрямую как
 * {@code minValue}/{@code maxValue} (абсолютные значения), а не в процентах от цели,
 * как в {@link RecipeItem} — у параметров качества (влажность, прочность,
 * газопроницаемость) нет единой "целевой" величины, только приемлемый коридор.
 */
@Entity
@Table(name = "quality_spec_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualitySpecItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quality_spec_id", nullable = false)
    private QualitySpec qualitySpec;

    /** Код параметра для сопоставления с распознанным текстом (аналог RecipeItem.materialCode). */
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

    /** NUMERIC — диапазон min/max; QUALITATIVE — значение из списка допустимых (например, «внешний вид»). */
    @Enumerated(EnumType.STRING)
    @Column(name = "parameter_type", nullable = false, length = 20)
    @Builder.Default
    private com.mes.domain.enums.QcParameterType parameterType = com.mes.domain.enums.QcParameterType.NUMERIC;

    /** Допустимые значения качественного параметра через «;» (сравнение без учёта регистра). */
    @Column(name = "allowed_values", length = 500)
    private String allowedValues;
}
