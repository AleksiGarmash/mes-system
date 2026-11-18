package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Одна строка протокола QC: параметр и измеренное значение, распознанные OCR
 * (и, при необходимости, скорректированные лаборантом вручную).
 */
@Entity
@Table(name = "qc_checklist_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QcChecklistItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private QcChecklist checklist;

    /** Ссылка на строку QC-спецификации, к которой относится этот параметр (после сопоставления). */
    @Column(name = "quality_spec_item_id")
    private UUID qualitySpecItemId;

    @Column(name = "parameter_name_raw", length = 255)
    private String parameterNameRaw;

    @Column(name = "measured_value", precision = 12, scale = 3)
    private BigDecimal measuredValue;

    /** Значение качественного параметра («однородная жидкость без осадка»). */
    @Column(name = "text_value", length = 255)
    private String textValue;

    @Column(name = "ocr_confidence")
    private Double confidence;

    /** Требуется ли ручная проверка лаборантом (низкая уверенность OCR / параметр не найден / вне диапазона). */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "within_range")
    private Boolean withinRange;

    @Column(name = "corrected_by_operator", nullable = false)
    private boolean correctedByOperator;
}
