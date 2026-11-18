package com.mes.domain;

import com.mes.domain.enums.QcChecklistStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Цифровая оболочка бумажного протокола контроля качества: фото/скан заполненного
 * бланка плюс распознанные и подтверждённые результаты испытаний. Как и
 * {@link ProductionChecklist}, официальным первичным документом остаётся бумажный лист.
 * <p>
 * Один протокол — одна партия (уникальность {@code batch_id} обеспечена на уровне БД):
 * по требованиям проекта QC не разбивается на несколько параллельных протоколов на одну партию.
 */
@Entity
@Table(name = "qc_checklists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QcChecklist {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false, unique = true)
    private Batch batch;

    /** QC-спецификация, актуальная (ACTIVE) на момент создания протокола. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quality_spec_id")
    private QualitySpec qualitySpec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private com.mes.domain.enums.QcChecklistStatus status;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "file_content_type", length = 100)
    private String fileContentType;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "uploaded_by", length = 255)
    private String uploadedBy;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "confirmed_by", length = 255)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    @Column(name = "qr_verified")
    private Boolean qrVerified;

    @Column(name = "page_count")
    private Integer pageCount;

    /** Поля бланка «решение по партии», «мероприятия по несоответствиям» и вторая подпись (свободный текст). */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "nonconformity_actions", length = 1000)
    private String nonconformityActions;

    @Column(name = "second_signatory", length = 255)
    private String secondSignatory;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QcChecklistItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) status = QcChecklistStatus.NEW;
    }

    public void addItem(QcChecklistItem item) {
        items.add(item);
        item.setChecklist(this);
    }
}
