package com.mes.domain;

import com.mes.domain.enums.ChecklistStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Цифровая оболочка бумажного чек-листа: фото/скан заполненного бланка задания
 * плюс распознанные и подтверждённые данные. Официальным первичным документом
 * остаётся бумажный лист — эта сущность лишь его цифровое представление.
 */
@Entity
@Table(name = "production_checklists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionChecklist {

    @Id
    @GeneratedValue
    private UUID id;

    /** Оптимистичная блокировка: параллельный confirm/upload получит конфликт версий (409). */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChecklistStatus status;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    /** SHA-256 загруженного файла и его тип: recognize читает файл из хранилища и сверяет хэш. */
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

    @Column(name = "batch_id")
    private UUID batchId;

    /**
     * Был ли на бланке найден и успешно проверен QR-код задания (QR №2).
     * {@code null} — QR не искали/бланк без QR; {@code true} — найден и совпал с заданием;
     * {@code false} никогда не сохраняется — при несовпадении процесс останавливается
     * исключением ({@link com.mes.exception.QrVerificationException}) раньше, чем дойдёт до save().
     */
    @Column(name = "qr_verified")
    private Boolean qrVerified;

    @Column(name = "page_count")
    private Integer pageCount;

    /** Предупреждения уровня листа (например, сумма масс стадии не сходится с планом). Не блокируют подтверждение. */
    @Column(name = "review_summary", length = 1000)
    private String reviewSummary;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessControlRecord> controlRecords = new ArrayList<>();

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChecklistItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) status = ChecklistStatus.NEW;
    }

    public void addControlRecord(ProcessControlRecord record) {
        controlRecords.add(record);
        record.setChecklist(this);
    }

    public void addItem(ChecklistItem item) {
        items.add(item);
        item.setChecklist(this);
    }
}
