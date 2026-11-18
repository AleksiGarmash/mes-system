package com.mes.service;

import com.mes.domain.*;
import com.mes.domain.enums.BatchStatus;
import com.mes.domain.enums.QcChecklistStatus;
import com.mes.domain.enums.QcParameterType;
import com.mes.domain.enums.QcResult;
import com.mes.dto.request.ConfirmQcChecklistItemRequest;
import com.mes.dto.request.ConfirmQcChecklistRequest;
import com.mes.exception.InvalidRequestException;
import com.mes.exception.InvalidStateTransitionException;
import com.mes.exception.QrVerificationException;
import com.mes.exception.ResourceNotFoundException;
import com.mes.ocr.OcrDocumentResult;
import com.mes.ocr.OcrEngine;
import com.mes.ocr.OcrLineItem;
import com.mes.pdf.PdfPageRasterizer;
import com.mes.qr.BatchQrPayload;
import com.mes.qr.QrCodeService;
import com.mes.repository.*;
import com.mes.dto.response.StoredFile;
import com.mes.util.FileHashing;
import com.mes.util.FileTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ядро "Document-first QC": бумажный протокол контроля качества остаётся официальным
 * первичным документом, эта служба превращает его фото/скан в проверенную электронную
 * запись и создаёт {@link QualityCheck} на каждый параметр.
 * <p>
 * Зеркалирует {@code ChecklistService} (производственный чек-лист) по структуре —
 * тот же QR-первый подход к идентификации документа, тот же порог уверенности OCR,
 * та же схема needsReview/correctedByOperator. Отличия по существу: допуск — диапазон
 * {@code min}/{@code max} ({@link RangeValidator}), а не проценты от цели
 * ({@link DosageValidator}); один протокол строго на одну партию (не версионируется
 * как производственный чек-лист, который можно переснять).
 * <p>
 * <b>Переиспользование OCR-модели:</b> {@link OcrLineItem} спроектирован под тройку
 * "материал/партия/вес" для производственного потока. Здесь он переиспользуется с другим
 * смыслом полей: {@code materialName} → название параметра QC, {@code weight} → измеренное
 * значение, {@code batchNumber} не используется (всегда {@code null}). Это сознательное
 * упрощение, чтобы не дублировать весь {@code com.mes.ocr}-пакет ради второго похожего
 * по форме документа; если набор QC-полей усложнится (например, появится единица измерения
 * как отдельное поле), стоит завести отдельный {@code QcOcrLineItem}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QcChecklistService {

    static final String AUDIT_PROTOCOL = "QC_CHECKLIST";
    static final String AUDIT_ITEM = "QC_CHECKLIST_ITEM";

    private static final double CONFIDENCE_THRESHOLD = 0.90;

    private final QcChecklistRepository checklistRepository;
    private final QcChecklistItemRepository checklistItemRepository;
    private final BatchRepository batchRepository;
    private final QualitySpecRepository qualitySpecRepository;
    private final QualitySpecItemRepository qualitySpecItemRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final OcrEngine ocrEngine;
    private final ChecklistStorageService storageService;
    private final RangeValidator rangeValidator;
    private final QualityControlService qualityControlService;
    private final QrCodeService qrCodeService;
    private final PdfPageRasterizer pdfPageRasterizer;
    private final AuditService auditService;

    /** Шаг 1: создать протокол QC для партии (перед печатью бланка). Одна партия — один протокол. */
    @Transactional
    public QcChecklist create(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Партия не найдена: " + batchId));

        checklistRepository.findByBatchId(batchId).ifPresent(existing -> {
            throw new InvalidStateTransitionException(
                    "Протокол QC для партии " + batch.getBatchNo() + " уже существует: " + existing.getId());
        });

        String specCode = batch.getProductionOrder().getRecipe().getQualitySpecCode();
        QualitySpec spec = null;
        if (specCode != null) {
            spec = qualitySpecRepository.findByCodeAndStatus(specCode, com.mes.domain.enums.QualitySpecStatus.ACTIVE)
                    .orElse(null);
            if (spec == null) {
                log.warn("Для рецептуры {} указана QC-спецификация {}, но активной версии не найдено.",
                        batch.getProductionOrder().getRecipe().getCode(), specCode);
            }
        } else {
            log.warn("Для рецептуры {} QC-спецификация не задана — протокол будет без эталонных параметров.",
                    batch.getProductionOrder().getRecipe().getCode());
        }

        QcChecklist checklist = QcChecklist.builder()
                .batch(batch)
                .qualitySpec(spec)
                .status(QcChecklistStatus.NEW)
                .build();

        return checklistRepository.save(checklist);
    }

    /** Шаг 2: лаборант загружает фото/скан заполненного протокола (изображение или PDF). */
    @Transactional
    public QcChecklist upload(UUID checklistId, byte[] fileBytes, String contentType, String uploadedBy) {
        QcChecklist checklist = getById(checklistId);

        QcChecklistStatus status = checklist.getStatus();
        if (status != QcChecklistStatus.NEW && status != QcChecklistStatus.UPLOADED
                && status != QcChecklistStatus.REJECTED) {
            throw new InvalidStateTransitionException(
                    "Загрузка файла недоступна для протокола в статусе " + status
                            + ": подтверждённый документ нельзя заменить.");
        }

        String sha256 = FileHashing.sha256Hex(fileBytes);
        checklistRepository.findFirstByFileSha256(sha256)
                .filter(other -> !other.getId().equals(checklistId))
                .ifPresent(other -> {
                    throw new InvalidRequestException(
                            "Этот файл уже загружен для другого протокола (" + other.getId() + ").");
                });

        int pageCount = pdfPageRasterizer.countPages(fileBytes, contentType);
        String imagePath = storageService.store(checklistId, fileBytes, contentType);

        checklist.getItems().clear();
        checklist.setQrVerified(null);
        checklist.setRejectReason(null);
        checklist.setOcrConfidence(null);

        checklist.setImagePath(imagePath);
        checklist.setFileSha256(sha256);
        checklist.setFileContentType(contentType);
        checklist.setUploadedBy(uploadedBy);
        checklist.setUploadedAt(Instant.now());
        checklist.setPageCount(pageCount);
        checklist.setStatus(QcChecklistStatus.UPLOADED);
        auditService.recordStatus(AUDIT_PROTOCOL, checklistId, status, QcChecklistStatus.UPLOADED);

        return checklistRepository.save(checklist);
    }

    /**
     * Шаг 3: распознавание. Порядок — как в ChecklistService: сначала QR (здесь —
     * {@link BatchQrPayload}, сверяется с партией протокола), затем OCR по всем страницам.
     */
    @Transactional
    public QcChecklist recognize(UUID checklistId) {
        QcChecklist checklist = getById(checklistId);

        if (checklist.getStatus() != QcChecklistStatus.UPLOADED) {
            throw new InvalidStateTransitionException(
                    "Распознавание доступно только для протокола в статусе UPLOADED, текущий: " + checklist.getStatus());
        }

        // Распознаётся файл из хранилища (первичный документ), а не присланный повторно клиентом.
        byte[] fileBytes = storageService.retrieve(checklist.getImagePath());
        if (!FileHashing.sha256Hex(fileBytes).equals(checklist.getFileSha256())) {
            throw new InvalidStateTransitionException(
                    "Контрольная сумма файла в хранилище не совпадает с загруженной — документ изменён или повреждён.");
        }
        String contentType = checklist.getFileContentType();

        List<BufferedImage> pages = toPages(fileBytes, contentType);
        if (pages.isEmpty()) {
            throw new InvalidStateTransitionException("Не удалось прочитать ни одной страницы из загруженного файла.");
        }

        verifyBatchQrCode(checklist, pages.get(0));

        QualitySpec spec = checklist.getQualitySpec();
        List<OcrLineItem> allLineItems = new ArrayList<>();
        double confidenceSum = 0;

        for (BufferedImage page : pages) {
            OcrDocumentResult pageResult = ocrEngine.recognize(toPng(page));
            allLineItems.addAll(pageResult.lineItems());
            confidenceSum += pageResult.overallConfidence();
        }

        checklist.getItems().clear();
        for (OcrLineItem line : allLineItems) {
            checklist.addItem(buildAndValidateItem(line, spec));
        }
        addMissingSpecItems(checklist, spec);

        checklist.setOcrConfidence(pages.isEmpty() ? 0.0 : confidenceSum / pages.size());
        checklist.setStatus(QcChecklistStatus.OCR_COMPLETE);
        checklistRepository.save(checklist);

        checklist.setStatus(QcChecklistStatus.WAITING_CONFIRMATION);
        return checklistRepository.save(checklist);
    }

    private void verifyBatchQrCode(QcChecklist checklist, BufferedImage firstPage) {
        Optional<BatchQrPayload> qr = qrCodeService.decode(firstPage, BatchQrPayload.class);

        if (qr.isEmpty()) {
            log.debug("QR-код партии не найден на протоколе QC {} — бланк без QR либо старого образца.", checklist.getId());
            checklist.setQrVerified(null);
            return;
        }

        UUID expectedBatchId = checklist.getBatch().getId();
        if (!qr.get().batchId().equals(expectedBatchId)) {
            throw new QrVerificationException(
                    "QR-код на протоколе относится к другой партии (%s), ожидалась партия %s. Проверьте, тот ли лист загружен."
                            .formatted(qr.get().batchNo(), checklist.getBatch().getBatchNo()));
        }

        checklist.setQrVerified(true);
    }

    private List<BufferedImage> toPages(byte[] fileBytes, String contentType) {
        if (pdfPageRasterizer.isPdf(contentType, fileBytes)) {
            return pdfPageRasterizer.rasterize(fileBytes);
        }
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(fileBytes));
            return image != null ? List.of(image) : List.of();
        } catch (IOException e) {
            throw new InvalidStateTransitionException("Не удалось прочитать изображение протокола QC: " + e.getMessage());
        }
    }

    private byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось конвертировать страницу в PNG для OCR", e);
        }
    }

    /**
     * Строка протокола из распознанной строки OCR. Переиспользование {@link OcrLineItem}:
     * {@code materialName} — название параметра, {@code weight} — числовое значение,
     * {@code batchNumber} — текстовое значение качественного параметра («внешний вид»).
     */
    private QcChecklistItem buildAndValidateItem(OcrLineItem line, QualitySpec spec) {
        String parameterName = line.materialName() != null ? line.materialName().value() : null;
        BigDecimal measuredValue = line.weight() != null ? line.weight().value() : null;
        String textValue = line.batchNumber() != null ? line.batchNumber().value() : null;
        double confidence = averageConfidence(line);

        QcChecklistItem.QcChecklistItemBuilder builder = QcChecklistItem.builder()
                .parameterNameRaw(parameterName)
                .measuredValue(measuredValue)
                .textValue(textValue)
                .confidence(confidence)
                .correctedByOperator(false);

        StringBuilder reviewNote = new StringBuilder();
        boolean needsReview = false;

        if (parameterName == null || (measuredValue == null && (textValue == null || textValue.isBlank()))) {
            needsReview = true;
            reviewNote.append("OCR не распознал параметр или значение. ");
        }
        if (confidence < CONFIDENCE_THRESHOLD) {
            needsReview = true;
            reviewNote.append("Низкая уверенность распознавания (%.0f%%). ".formatted(confidence * 100));
        }

        QualitySpecItem matchedSpecItem = null;
        if (parameterName != null && spec != null) {
            matchedSpecItem = spec.getItems().stream()
                    .filter(si -> si.getParameterCode() != null &&
                            si.getParameterCode().equalsIgnoreCase(parameterName.trim()))
                    .findFirst()
                    .orElse(null);
        }

        if (matchedSpecItem == null) {
            needsReview = true;
            reviewNote.append("Параметр не найден в QC-спецификации — требуется ручное сопоставление. ");
        } else {
            builder.qualitySpecItemId(matchedSpecItem.getId());

            if (isQualitative(matchedSpecItem)) {
                if (textValue != null && !textValue.isBlank()) {
                    boolean allowed = rangeValidator.isAllowedValue(textValue, matchedSpecItem.getAllowedValues());
                    builder.withinRange(allowed);
                    if (!allowed) {
                        needsReview = true;
                        reviewNote.append("Значение не входит в допустимые для этого параметра. ");
                    }
                }
            } else if (measuredValue != null) {
                boolean withinRange = rangeValidator.isWithinRange(
                        measuredValue, matchedSpecItem.getMinValue(), matchedSpecItem.getMaxValue());
                builder.withinRange(withinRange);
                if (!withinRange) {
                    needsReview = true;
                    reviewNote.append("Значение вне допуска QC-спецификации. ");
                }
            }
        }

        builder.needsReview(needsReview);
        builder.reviewNote(reviewNote.isEmpty() ? null : reviewNote.toString().trim());

        return builder.build();
    }

    private boolean isQualitative(QualitySpecItem specItem) {
        return specItem != null && specItem.getParameterType() == QcParameterType.QUALITATIVE;
    }

    /**
     * Добавляет пустые строки для параметров спецификации, не найденных OCR на листе, —
     * пропущенный параметр не может остаться незамеченным и протокол нельзя подтвердить неполным.
     */
    private void addMissingSpecItems(QcChecklist checklist, QualitySpec spec) {
        if (spec == null) {
            return;
        }
        Set<UUID> covered = new HashSet<>();
        for (QcChecklistItem item : checklist.getItems()) {
            if (item.getQualitySpecItemId() != null) {
                covered.add(item.getQualitySpecItemId());
            }
        }
        for (QualitySpecItem specItem : spec.getItems()) {
            if (!covered.contains(specItem.getId())) {
                checklist.addItem(QcChecklistItem.builder()
                        .qualitySpecItemId(specItem.getId())
                        .parameterNameRaw(specItem.getParameterName())
                        .confidence(0.0)
                        .needsReview(true)
                        .reviewNote("Параметр не найден на листе — внесите значение вручную.")
                        .correctedByOperator(false)
                        .build());
            }
        }
    }

    private double averageConfidence(OcrLineItem line) {
        double sum = 0;
        int count = 0;
        if (line.materialName() != null) { sum += line.materialName().confidence(); count++; }
        if (line.weight() != null) { sum += line.weight().confidence(); count++; }
        if (line.batchNumber() != null) { sum += line.batchNumber().confidence(); count++; }
        return count == 0 ? 0.0 : sum / count;
    }

    @Transactional(readOnly = true)
    public QcChecklist getById(UUID id) {
        return checklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Протокол QC не найден: " + id));
    }

    /**
     * Шаг 4: лаборант подтверждает протокол — применяет ручные исправления к строкам,
     * где OCR ошибся или не смог распознать поле, создаёт QualityCheck на каждый параметр
     * и пересчитывает статус партии (RELEASED/REJECTED) тем же методом, что использует
     * ручной real-time ввод ({@link QualityControlService#updateBatchStatus(Batch)}).
     * Решение по партии, мероприятия по несоответствиям и вторая подпись сохраняются как
     * есть (свободный текст бланка) и на статус партии не влияют: его определяют результаты.
     */
    @Transactional
    public QcChecklist confirm(UUID checklistId, ConfirmQcChecklistRequest request) {
        QcChecklist checklist = getById(checklistId);

        if (checklist.getStatus() != QcChecklistStatus.WAITING_CONFIRMATION) {
            throw new InvalidStateTransitionException(
                    "Подтверждение доступно только из статуса WAITING_CONFIRMATION, текущий: " + checklist.getStatus());
        }

        if (request.corrections() != null) {
            for (ConfirmQcChecklistItemRequest correction : request.corrections()) {
                applyCorrection(checklist, correction);
            }
        }

        if (checklist.getItems().isEmpty()) {
            throw new InvalidStateTransitionException(
                    "В протоколе нет ни одной строки — подтверждать нечего. Проверьте распознавание.");
        }

        boolean anyStillIncomplete = checklist.getItems().stream().anyMatch(i -> {
            QualitySpecItem specItem = findSpecItem(checklist, i.getQualitySpecItemId());
            return isQualitative(specItem)
                    ? i.getTextValue() == null || i.getTextValue().isBlank()
                    : i.getMeasuredValue() == null;
        });

        if (anyStillIncomplete) {
            throw new InvalidStateTransitionException(
                    "Есть незаполненные строки протокола — заполните значения всех параметров перед подтверждением.");
        }

        checklist.setConfirmedBy(request.labTechnician());
        checklist.setConfirmedAt(Instant.now());
        checklist.setDecisionNote(request.decisionNote());
        checklist.setNonconformityActions(request.nonconformityActions());
        checklist.setSecondSignatory(request.secondSignatory());
        checklist.setStatus(QcChecklistStatus.CONFIRMED);
        checklistRepository.save(checklist);
        auditService.recordStatus(AUDIT_PROTOCOL, checklistId, QcChecklistStatus.WAITING_CONFIRMATION, QcChecklistStatus.CONFIRMED);

        createQualityChecksAndUpdateBatch(checklist);

        return checklist;
    }

    /** Ищет строку спецификации сначала в уже загруженной спецификации протокола, затем в БД. */
    private QualitySpecItem findSpecItem(QcChecklist checklist, UUID specItemId) {
        if (specItemId == null) {
            return null;
        }
        if (checklist.getQualitySpec() != null) {
            for (QualitySpecItem si : checklist.getQualitySpec().getItems()) {
                if (specItemId.equals(si.getId())) {
                    return si;
                }
            }
        }
        return qualitySpecItemRepository.findById(specItemId).orElse(null);
    }

    private void applyCorrection(QcChecklist checklist, ConfirmQcChecklistItemRequest correction) {
        QcChecklistItem item = checklist.getItems().stream()
                .filter(i -> i.getId().equals(correction.checklistItemId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Строка протокола QC не найдена: " + correction.checklistItemId()));

        UUID oldSpecItem = item.getQualitySpecItemId();
        BigDecimal oldMeasured = item.getMeasuredValue();
        String oldText = item.getTextValue();

        if (correction.qualitySpecItemId() != null) item.setQualitySpecItemId(correction.qualitySpecItemId());
        if (correction.measuredValue() != null) item.setMeasuredValue(correction.measuredValue());
        if (correction.textValue() != null) item.setTextValue(correction.textValue());

        auditService.recordChange(AUDIT_ITEM, item.getId(), "qualitySpecItemId", oldSpecItem, item.getQualitySpecItemId());
        auditService.recordChange(AUDIT_ITEM, item.getId(), "measuredValue", oldMeasured, item.getMeasuredValue());
        auditService.recordChange(AUDIT_ITEM, item.getId(), "textValue", oldText, item.getTextValue());

        QualitySpecItem specItem = findSpecItem(checklist, item.getQualitySpecItemId());
        if (specItem != null) {
            if (isQualitative(specItem)) {
                if (item.getTextValue() != null && !item.getTextValue().isBlank()) {
                    item.setWithinRange(rangeValidator.isAllowedValue(item.getTextValue(), specItem.getAllowedValues()));
                }
            } else if (item.getMeasuredValue() != null) {
                item.setWithinRange(rangeValidator.isWithinRange(
                        item.getMeasuredValue(), specItem.getMinValue(), specItem.getMaxValue()));
            }
        }

        item.setCorrectedByOperator(true);
        item.setNeedsReview(false);
        checklistItemRepository.save(item);
    }

    /** Отклонить протокол (неразборчиво/некорректно) — печатается заново, партия остаётся в PENDING_QC. */
    @Transactional
    public QcChecklist reject(UUID checklistId, String reason) {
        QcChecklist checklist = getById(checklistId);
        QcChecklistStatus before = checklist.getStatus();
        if (before == QcChecklistStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("Подтверждённый протокол нельзя отклонить.");
        }
        checklist.setStatus(QcChecklistStatus.REJECTED);
        checklist.setRejectReason(reason);
        auditService.recordChange(AUDIT_PROTOCOL, checklistId, "status", before, QcChecklistStatus.REJECTED);
        auditService.recordChange(AUDIT_PROTOCOL, checklistId, "rejectReason", null, reason);
        return checklistRepository.save(checklist);
    }

    private void createQualityChecksAndUpdateBatch(QcChecklist checklist) {
        Batch batch = checklist.getBatch();

        for (QcChecklistItem item : checklist.getItems()) {
            QualitySpecItem specItem = findSpecItem(checklist, item.getQualitySpecItemId());
            boolean qualitative = isQualitative(specItem);
            boolean withinRange = Boolean.TRUE.equals(item.getWithinRange());

            QualityCheck check = QualityCheck.builder()
                    .batch(batch)
                    .parameter(specItem != null ? specItem.getParameterName() : item.getParameterNameRaw())
                    .expectedMin(specItem != null && !qualitative ? specItem.getMinValue() : null)
                    .expectedMax(specItem != null && !qualitative ? specItem.getMaxValue() : null)
                    .expectedText(qualitative ? specItem.getAllowedValues() : null)
                    .measuredValue(qualitative ? null : item.getMeasuredValue())
                    .textValue(qualitative ? item.getTextValue() : null)
                    .result(withinRange ? QcResult.PASS : QcResult.FAIL)
                    .labTechnician(checklist.getConfirmedBy())
                    .build();

            qualityCheckRepository.save(check);
        }

        qualityControlService.updateBatchStatus(batch);
    }

    @Transactional(readOnly = true)
    public Page<QcChecklist> list(QcChecklistStatus status, Pageable pageable) {
        return status != null ? checklistRepository.findByStatus(status, pageable) : checklistRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public StoredFile getFile(UUID checklistId) {
        QcChecklist checklist = getById(checklistId);
        if (checklist.getImagePath() == null) {
            throw new ResourceNotFoundException("Файл ещё не загружен для протокола " + checklistId);
        }
        String contentType = checklist.getFileContentType() != null
                ? checklist.getFileContentType() : "application/octet-stream";
        return new StoredFile(storageService.retrieve(checklist.getImagePath()), contentType,
                "qc-protocol-" + checklistId + "." + FileTypes.extensionFor(contentType));
    }
}
