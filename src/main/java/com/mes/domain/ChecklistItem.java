package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Одна строка чек-листа: компонент, номер партии сырья и фактический вес,
 * распознанные OCR (и, при необходимости, скорректированные оператором вручную).
 */
@Entity
@Table(name = "checklist_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private ProductionChecklist checklist;

    /** Ссылка на строку рецептуры, к которой относится этот компонент (после сопоставления). */
    @Column(name = "recipe_item_id")
    private UUID recipeItemId;

    @Column(name = "material_name_raw", length = 255)
    private String materialNameRaw;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "batch_number_raw", length = 100)
    private String batchNumberRaw;

    @Column(name = "raw_material_batch_id")
    private UUID rawMaterialBatchId;

    @Column(name = "weight", precision = 12, scale = 3)
    private BigDecimal weight;

    @Column(name = "ocr_confidence")
    private Double confidence;

    /** Требуется ли ручная коррекция оператором (низкая уверенность OCR / поле не найдено / не прошло валидацию). */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "within_tolerance")
    private Boolean withinTolerance;

    @Column(name = "corrected_by_operator", nullable = false)
    private boolean correctedByOperator;

    /** Фактические обороты вала и длительность перемешивания, переписанные оператором с бланка. */
    @Column(name = "mix_speed_actual")
    private Integer mixSpeedActual;

    @Column(name = "mix_minutes_actual")
    private Integer mixMinutesActual;

    @Column(name = "note", length = 500)
    private String note;
}
