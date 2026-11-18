package com.mes.domain;

import com.mes.domain.enums.QcResult;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quality_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityCheck {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(name = "parameter", nullable = false, length = 100)
    private String parameter;

    /** Используется ручным real-time вводом ({@code POST /api/qc/{batchId}}) — точное ожидаемое значение. */
    @Column(name = "expected_value", precision = 12, scale = 3)
    private BigDecimal expectedValue;

    /**
     * Используются document-first потоком (QC-чек-лист): диапазон допуска из
     * {@link QualitySpecItem}, действовавший на момент проверки — сохраняется в саму
     * запись (а не читается заново из спецификации), чтобы паспорт партии оставался
     * корректным даже если спецификацию позже изменят новой версией.
     */
    @Column(name = "expected_min", precision = 12, scale = 3)
    private BigDecimal expectedMin;

    @Column(name = "expected_max", precision = 12, scale = 3)
    private BigDecimal expectedMax;

    @Column(name = "measured_value", precision = 12, scale = 3)
    private BigDecimal measuredValue;

    /** Для качественных параметров: допустимые значения (снимок) и зафиксированное значение. */
    @Column(name = "expected_text", length = 500)
    private String expectedText;

    @Column(name = "text_value", length = 255)
    private String textValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QcResult result;

    @Column(name = "lab_technician", length = 255)
    private String labTechnician;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @PrePersist
    void onCreate() {
        if (result == null) result = QcResult.PENDING;
        if (checkedAt == null) checkedAt = Instant.now();
    }
}
