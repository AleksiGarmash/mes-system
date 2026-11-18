package com.mes.service;

import com.mes.domain.*;
import com.mes.domain.enums.BatchStatus;
import com.mes.domain.enums.ChecklistStatus;
import com.mes.domain.enums.ControlPointKind;
import com.mes.domain.enums.MixingSessionStatus;
import com.mes.dto.request.ConfirmChecklistItemRequest;
import com.mes.dto.request.ConfirmChecklistRequest;
import com.mes.dto.request.ControlValueRequest;
import com.mes.event.MesEvent;
import com.mes.event.MesEventPublisher;
import com.mes.exception.InvalidRequestException;
import com.mes.exception.InvalidStateTransitionException;
import com.mes.exception.QrVerificationException;
import com.mes.exception.ResourceNotFoundException;
import com.mes.ocr.OcrDocumentResult;
import com.mes.ocr.OcrEngine;
import com.mes.ocr.OcrLineItem;
import com.mes.pdf.PdfPageRasterizer;
import com.mes.qr.OrderQrPayload;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ядро "Document-first MES": бумажный чек-лист остаётся официальным первичным
 * документом, эта служба лишь превращает его фото/скан в проверенную электронную запись.
 * <p>
 * Порог уверенности OCR, ниже которого поле считается ненадёжным и требует
 * подтверждения/правки оператором.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChecklistService {

    static final String AUDIT_CHECKLIST = "PRODUCTION_CHECKLIST";
    static final String AUDIT_ITEM = "PRODUCTION_CHECKLIST_ITEM";

    private static final double CONFIDENCE_THRESHOLD = 0.90;

    /** Допустимое расхождение суммы масс стадии с планом (округление процентов на бланке): 2%. */
    private static final BigDecimal STAGE_MASS_TOLERANCE = new BigDecimal("0.02");

    private final ProductionChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ProductionOrderRepository orderRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final RawMaterialBatchRepository rawMaterialBatchRepository;
    private final BatchRepository batchRepository;
    private final MixingSessionRepository mixingSessionRepository;
    private final WeighingRecordRepository weighingRecordRepository;
    private final OcrEngine ocrEngine;
    private final ChecklistStorageService storageService;
    private final DosageValidator dosageValidator;
    private final MesEventPublisher eventPublisher;
    private final QrCodeService qrCodeService;
    private final PdfPageRasterizer pdfPageRasterizer;
    private final RangeValidator rangeValidator;
    private final AuditService auditService;
    private final BatchNumberService batchNumberService;

    /** Шаг 1: создать чек-лист для производственного задания (перед печатью бланка). */
    @Transactional
    public ProductionChecklist create(UUID productionOrderId) {
        ProductionOrder order = orderRepository.findById(productionOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Задание не найдено: " + productionOrderId));

        ProductionChecklist checklist = ProductionChecklist.builder()
                .productionOrder(order)
                .status(ChecklistStatus.NEW)
                .build();

        return checklistRepository.save(checklist);
    }

    /** Шаг 2: оператор загружает фото/скан заполненного бланка (изображение или PDF, в т.ч. многостраничный). */
    @Transactional
    public ProductionChecklist upload(UUID checklistId, byte[] fileBytes, String contentType, String uploadedBy) {
        ProductionChecklist checklist = getById(checklistId);

        ChecklistStatus status = checklist.getStatus();
        if (status != ChecklistStatus.NEW && status != ChecklistStatus.UPLOADED && status != ChecklistStatus.REJECTED) {
            throw new InvalidStateTransitionException(
                    "Загрузка файла недоступна для чек-листа в статусе " + status
                            + ": подтверждённый документ нельзя заменить.");
        }

        String sha256 = FileHashing.sha256Hex(fileBytes);
        checklistRepository.findFirstByFileSha256(sha256)
                .filter(other -> !other.getId().equals(checklistId))
                .ifPresent(other -> {
                    throw new InvalidRequestException(
                            "Этот файл уже загружен для другого чек-листа (" + other.getId() + ").");
                });

        int pageCount = pdfPageRasterizer.countPages(fileBytes, contentType);
        String imagePath = storageService.store(checklistId, fileBytes, contentType);

        // повторная загрузка заменяет предыдущую: распознанные строки теряют актуальность
        checklist.getItems().clear();
        checklist.setReviewSummary(null);
        checklist.setQrVerified(null);
        checklist.setRejectReason(null);
        checklist.setOcrConfidence(null);

        checklist.setImagePath(imagePath);
        checklist.setFileSha256(sha256);
        checklist.setFileContentType(contentType);
        checklist.setUploadedBy(uploadedBy);
        checklist.setUploadedAt(Instant.now());
        checklist.setPageCount(pageCount);
        checklist.setStatus(ChecklistStatus.UPLOADED);
        auditService.recordStatus(AUDIT_CHECKLIST, checklistId, status, ChecklistStatus.UPLOADED);

        return checklistRepository.save(checklist);
    }

    /**
     * Шаг 3: запустить распознавание и сверку с рецептурой.
     * <p>
     * Порядок действий:
     * <ol>
     *   <li>Если пришёл PDF — растеризовать все страницы в изображения; если фото — одна "страница".</li>
     *   <li>На первой странице искать QR-код задания. Если найден — сверить с заданием чек-листа
     *       (несовпадение = {@link QrVerificationException}, процесс останавливается). Если не найден —
     *       не ошибка, просто {@code qrVerified} останется {@code null} (бланк без QR или старого образца).</li>
     *   <li>Прогнать OCR по каждой странице отдельно и объединить распознанные строки компонентов —
     *       так поддерживаются чек-листы на 2+ страницах.</li>
     * </ol>
     */
    @Transactional
    public ProductionChecklist recognize(UUID checklistId) {
        ProductionChecklist checklist = getById(checklistId);

        if (checklist.getStatus() != ChecklistStatus.UPLOADED) {
            throw new InvalidStateTransitionException(
                    "Распознавание доступно только для чек-листа в статусе UPLOADED, текущий: " + checklist.getStatus());
        }

        // Распознаётся именно тот файл, что сохранён как первичный документ, а не тот, что прислал клиент.
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

        verifyOrderQrCode(checklist, pages.get(0));

        Recipe recipe = checklist.getProductionOrder().getRecipe();
        List<OcrLineItem> allLineItems = new ArrayList<>();
        double confidenceSum = 0;

        for (BufferedImage page : pages) {
            OcrDocumentResult pageResult = ocrEngine.recognize(toPng(page));
            allLineItems.addAll(pageResult.lineItems());
            confidenceSum += pageResult.overallConfidence();
        }

        checklist.getItems().clear();
        for (OcrLineItem line : allLineItems) {
            checklist.addItem(buildAndValidateItem(line, recipe));
        }
        addMissingRecipeItems(checklist, recipe);
        checklist.setReviewSummary(buildMassWarnings(checklist, recipe));

        checklist.setOcrConfidence(pages.isEmpty() ? 0.0 : confidenceSum / pages.size());
        checklist.setStatus(ChecklistStatus.OCR_COMPLETE);
        checklistRepository.save(checklist);

        // Сразу переводим в ожидание подтверждения — оператор видит результат и правит ошибки
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        return checklistRepository.save(checklist);
    }

    /**
     * Ищет QR-код задания на первой странице и, если найден, сверяет с заданием, к которому
     * привязан этот чек-лист. Несовпадение — явная ошибка процесса (не тот лист попал в объектив),
     * а не повод молча продолжить с неверными данными.
     */
    private void verifyOrderQrCode(ProductionChecklist checklist, BufferedImage firstPage) {
        Optional<OrderQrPayload> qr = qrCodeService.decode(firstPage, OrderQrPayload.class);

        if (qr.isEmpty()) {
            log.debug("QR-код задания не найден на чек-листе {} — бланк без QR либо старого образца.", checklist.getId());
            checklist.setQrVerified(null);
            return;
        }

        UUID expectedOrderId = checklist.getProductionOrder().getId();
        if (!qr.get().orderId().equals(expectedOrderId)) {
            throw new QrVerificationException(
                    "QR-код на бланке относится к другому заданию (%s), ожидалось задание %s. Проверьте, тот ли лист загружен."
                            .formatted(qr.get().orderNo(), checklist.getProductionOrder().getOrderNo()));
        }

        checklist.setQrVerified(true);
        log.debug("QR-код задания подтверждён для чек-листа {}: {}", checklist.getId(), qr.get());
    }

    private List<BufferedImage> toPages(byte[] fileBytes, String contentType) {
        if (pdfPageRasterizer.isPdf(contentType, fileBytes)) {
            return pdfPageRasterizer.rasterize(fileBytes);
        }
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(fileBytes));
            return image != null ? List.of(image) : List.of();
        } catch (IOException e) {
            throw new InvalidStateTransitionException("Не удалось прочитать изображение чек-листа: " + e.getMessage());
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

    private ChecklistItem buildAndValidateItem(OcrLineItem line, Recipe recipe) {
        String materialName = line.materialName() != null ? line.materialName().value() : null;
        String batchNumber = line.batchNumber() != null ? line.batchNumber().value() : null;
        BigDecimal weight = line.weight() != null ? line.weight().value() : null;
        double confidence = averageConfidence(line);

        ChecklistItem.ChecklistItemBuilder builder = ChecklistItem.builder()
                .materialNameRaw(materialName)
                .batchNumberRaw(batchNumber)
                .weight(weight)
                .confidence(confidence)
                .correctedByOperator(false);

        StringBuilder reviewNote = new StringBuilder();
        boolean needsReview = false;

        if (!line.isComplete()) {
            needsReview = true;
            reviewNote.append("OCR не распознал одно или несколько полей. ");
        }
        if (confidence < CONFIDENCE_THRESHOLD) {
            needsReview = true;
            reviewNote.append("Низкая уверенность распознавания (%.0f%%). ".formatted(confidence * 100));
        }

        // Сопоставление с рецептурой по названию компонента (упрощённо; в реальной системе —
        // по справочнику синонимов материалов)
        RecipeItem matchedRecipeItem = null;
        if (materialName != null) {
            matchedRecipeItem = recipe.getItems().stream()
                    .filter(ri -> ri.getMaterialCode() != null &&
                            ri.getMaterialCode().equalsIgnoreCase(materialName.trim()))
                    .findFirst()
                    .orElse(null);
        }

        if (matchedRecipeItem == null) {
            needsReview = true;
            reviewNote.append("Компонент не найден в рецептуре — требуется ручное сопоставление. ");
        } else {
            builder.recipeItemId(matchedRecipeItem.getId());
            builder.materialId(matchedRecipeItem.getMaterialId());

            if (weight != null) {
                boolean withinTolerance = dosageValidator.isWithinTolerance(
                        matchedRecipeItem.getTargetQty(), weight,
                        matchedRecipeItem.getTolerancePlus(), matchedRecipeItem.getToleranceMinus());
                builder.withinTolerance(withinTolerance);
                if (!withinTolerance) {
                    needsReview = true;
                    reviewNote.append("Вес вне допуска рецептуры. ");
                }
            }
        }

        // Сопоставление с партией сырья по номеру лота
        if (batchNumber != null) {
            rawMaterialBatchRepository.findByLotNo(batchNumber.trim())
                    .ifPresentOrElse(
                            rmb -> builder.rawMaterialBatchId(rmb.getId()),
                            () -> {
                                // не найдена — оставляем needsReview, оператор подберёт вручную
                            });
        }

        builder.needsReview(needsReview);
        builder.reviewNote(reviewNote.isEmpty() ? null : reviewNote.toString().trim());

        return builder.build();
    }

    private double averageConfidence(OcrLineItem line) {
        double sum = 0;
        int count = 0;
        if (line.materialName() != null) { sum += line.materialName().confidence(); count++; }
        if (line.batchNumber() != null) { sum += line.batchNumber().confidence(); count++; }
        if (line.weight() != null) { sum += line.weight().confidence(); count++; }
        return count == 0 ? 0.0 : sum / count;
    }

    @Transactional(readOnly = true)
    public ProductionChecklist getById(UUID id) {
        return checklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Чек-лист не найден: " + id));
    }

    /**
     * Для каждого компонента рецептуры, не найденного OCR на листе, добавляет пустую строку
     * для ручного заполнения («вес не найден»). Так пропущенный компонент не теряется молча,
     * а лист с нераспознанным содержимым не может быть подтверждён пустым.
     */
    private void addMissingRecipeItems(ProductionChecklist checklist, Recipe recipe) {
        Set<UUID> covered = new HashSet<>();
        for (ChecklistItem item : checklist.getItems()) {
            if (item.getRecipeItemId() != null) {
                covered.add(item.getRecipeItemId());
            }
        }
        for (RecipeItem recipeItem : recipe.getItems()) {
            if (!covered.contains(recipeItem.getId())) {
                checklist.addItem(ChecklistItem.builder()
                        .recipeItemId(recipeItem.getId())
                        .materialId(recipeItem.getMaterialId())
                        .materialNameRaw(recipeItem.getMaterialCode())
                        .confidence(0.0)
                        .needsReview(true)
                        .reviewNote("Компонент не найден на листе — внесите значения вручную.")
                        .correctedByOperator(false)
                        .build());
            }
        }
    }

    /**
     * Сверяет сумму фактических масс компонентов каждой стадии с плановой массой стадии
     * (допуск {@link #STAGE_MASS_TOLERANCE}). Возвращает текст предупреждений или {@code null}.
     * Предупреждения не блокируют подтверждение: расхождение может быть реальным и должно
     * быть зафиксировано, а не скрыто.
     */
    private String buildMassWarnings(ProductionChecklist checklist, Recipe recipe) {
        List<String> warnings = new ArrayList<>();
        for (RecipeStage stage : recipe.getStages()) {
            if (stage.getPlannedQty() == null || stage.getPlannedQty().signum() == 0) {
                continue;
            }
            Set<UUID> stageItemIds = new HashSet<>();
            for (RecipeItem ri : recipe.getItems()) {
                if (ri.getStage() != null && stage.getStageNo().equals(ri.getStage().getStageNo())) {
                    stageItemIds.add(ri.getId());
                }
            }
            BigDecimal sum = BigDecimal.ZERO;
            boolean complete = !stageItemIds.isEmpty();
            for (ChecklistItem item : checklist.getItems()) {
                if (item.getRecipeItemId() != null && stageItemIds.contains(item.getRecipeItemId())) {
                    if (item.getWeight() == null) {
                        complete = false;
                    } else {
                        sum = sum.add(item.getWeight());
                    }
                }
            }
            if (!complete) {
                continue; // сумму можно оценить, только когда все веса стадии известны
            }
            BigDecimal deviation = sum.subtract(stage.getPlannedQty())
                    .divide(stage.getPlannedQty(), 4, RoundingMode.HALF_UP);
            if (deviation.abs().compareTo(STAGE_MASS_TOLERANCE) > 0) {
                warnings.add("Стадия %d: сумма масс %s кг, план %s кг (отклонение %s%%)."
                        .formatted(stage.getStageNo(), sum.stripTrailingZeros().toPlainString(),
                                stage.getPlannedQty().stripTrailingZeros().toPlainString(),
                                deviation.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)));
            }
        }
        return warnings.isEmpty() ? null : String.join(" ", warnings);
    }

    /**
     * Шаг 4: оператор подтверждает чек-лист — применяет ручные исправления к строкам,
     * где OCR ошибся или не смог распознать поле, вносит замеры контрольных точек и запускает
     * создание партии.
     * <p>
     * Условия подтверждения: есть хотя бы одна строка; у каждой строки заполнены материал и вес;
     * партия сырья обязательна только если рецептура этого требует
     * ({@link Recipe#getRequireRawMaterialLot()}); введены значения всех контрольных точек рецептуры.
     * Контрольная точка готового продукта вне допуска переводит партию в {@code QUARANTINE}
     * (допущение до подтверждения у технолога), точка процесса вне допуска только фиксируется
     * и попадает в предупреждения листа.
     */
    @Transactional
    public ProductionChecklist confirm(UUID checklistId, ConfirmChecklistRequest request) {
        ProductionChecklist checklist = getById(checklistId);

        if (checklist.getStatus() != ChecklistStatus.WAITING_CONFIRMATION) {
            throw new InvalidStateTransitionException(
                    "Подтверждение доступно только из статуса WAITING_CONFIRMATION, текущий: " + checklist.getStatus());
        }

        if (request.corrections() != null) {
            for (ConfirmChecklistItemRequest correction : request.corrections()) {
                applyCorrection(checklist, correction);
            }
        }

        Recipe recipe = checklist.getProductionOrder().getRecipe();
        boolean lotRequired = !Boolean.FALSE.equals(recipe.getRequireRawMaterialLot());

        List<ChecklistItem> items = checklist.getItems();
        if (items.isEmpty()) {
            throw new InvalidStateTransitionException(
                    "В чек-листе нет ни одной строки — подтверждать нечего. Проверьте распознавание.");
        }
        boolean anyStillIncomplete = items.stream().anyMatch(i ->
                i.getMaterialId() == null
                        || i.getWeight() == null
                        || (lotRequired && needsRawMaterialLot(recipe, i) && i.getRawMaterialBatchId() == null));
        if (anyStillIncomplete) {
            throw new InvalidStateTransitionException(lotRequired
                    ? "Есть незаполненные строки чек-листа — заполните материал, партию и вес перед подтверждением."
                    : "Есть незаполненные строки чек-листа — заполните материал и вес перед подтверждением.");
        }

        List<String> warnings = new ArrayList<>();
        boolean finishedProductOutOfRange = recordControlValues(checklist, recipe, request.controlValues(), warnings);

        String massWarnings = buildMassWarnings(checklist, recipe);
        if (massWarnings != null) {
            warnings.add(massWarnings);
        }

        checklist.setConfirmedBy(request.operator());
        checklist.setConfirmedAt(Instant.now());
        checklist.setStatus(ChecklistStatus.CONFIRMED);
        checklistRepository.save(checklist);
        auditService.recordStatus(AUDIT_CHECKLIST, checklistId, ChecklistStatus.WAITING_CONFIRMATION, ChecklistStatus.CONFIRMED);

        Batch batch = createBatchFromChecklist(checklist, warnings);
        checklist.setReviewSummary(warnings.isEmpty() ? null : String.join(" ", warnings));
        if (finishedProductOutOfRange) {
            batch.setStatus(BatchStatus.QUARANTINE);
            batchRepository.save(batch);
        }

        checklist.setBatchId(batch.getId());
        checklist.setStatus(ChecklistStatus.PRODUCTION_COMPLETED);
        return checklistRepository.save(checklist);
    }

    /**
     * Создаёт записи замеров по контрольным точкам рецептуры. Возвращает {@code true}, если
     * контрольная точка готового продукта вышла за допуск. Каждая точка рецептуры обязана
     * получить значение — иначе подтверждение отклоняется.
     */
    private boolean recordControlValues(ProductionChecklist checklist, Recipe recipe,
                                         List<ControlValueRequest> values, List<String> warnings) {
        List<RecipeControlPoint> points = recipe.getControlPoints();
        if (points.isEmpty()) {
            return false;
        }

        java.util.Map<UUID, BigDecimal> byPoint = new java.util.HashMap<>();
        if (values != null) {
            for (ControlValueRequest v : values) {
                byPoint.put(v.controlPointId(), v.measuredValue());
            }
        }

        List<String> missing = new ArrayList<>();
        for (RecipeControlPoint point : points) {
            if (!byPoint.containsKey(point.getId())) {
                missing.add(point.getParameterName());
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidStateTransitionException(
                    "Не введены значения контрольных точек: " + String.join(", ", missing));
        }

        boolean finishedProductOutOfRange = false;
        for (RecipeControlPoint point : points) {
            BigDecimal measured = byPoint.get(point.getId());
            boolean within = rangeValidator.isWithinRange(measured, point.getMinValue(), point.getMaxValue());

            checklist.addControlRecord(ProcessControlRecord.builder()
                    .controlPointId(point.getId())
                    .kind(point.getKind())
                    .stageNo(point.getStageNo())
                    .parameterName(point.getParameterName())
                    .expectedMin(point.getMinValue())
                    .expectedMax(point.getMaxValue())
                    .measuredValue(measured)
                    .withinRange(within)
                    .build());

            if (!within) {
                warnings.add("Контрольная точка «%s» вне допуска (%s).".formatted(
                        point.getParameterName(), measured.stripTrailingZeros().toPlainString()));
                if (point.getKind() == ControlPointKind.FINISHED_PRODUCT) {
                    finishedProductOutOfRange = true;
                }
            }
        }
        return finishedProductOutOfRange;
    }

    private void applyCorrection(ProductionChecklist checklist, ConfirmChecklistItemRequest correction) {
        ChecklistItem item = checklist.getItems().stream()
                .filter(i -> i.getId().equals(correction.checklistItemId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Строка чек-листа не найдена: " + correction.checklistItemId()));

        UUID oldMaterial = item.getMaterialId();
        UUID oldLot = item.getRawMaterialBatchId();
        BigDecimal oldWeight = item.getWeight();
        Integer oldSpeed = item.getMixSpeedActual();
        Integer oldMinutes = item.getMixMinutesActual();
        String oldNote = item.getNote();

        if (correction.materialId() != null) item.setMaterialId(correction.materialId());
        if (correction.rawMaterialBatchId() != null) item.setRawMaterialBatchId(correction.rawMaterialBatchId());
        if (correction.weight() != null) item.setWeight(correction.weight());
        if (correction.mixSpeedActual() != null) item.setMixSpeedActual(correction.mixSpeedActual());
        if (correction.mixMinutesActual() != null) item.setMixMinutesActual(correction.mixMinutesActual());
        if (correction.note() != null) item.setNote(correction.note());

        // Журнал правок: что именно оператор изменил относительно распознанного OCR.
        auditService.recordChange(AUDIT_ITEM, item.getId(), "materialId", oldMaterial, item.getMaterialId());
        auditService.recordChange(AUDIT_ITEM, item.getId(), "rawMaterialBatchId", oldLot, item.getRawMaterialBatchId());
        auditService.recordChange(AUDIT_ITEM, item.getId(), "weight", oldWeight, item.getWeight());
        auditService.recordChange(AUDIT_ITEM, item.getId(), "mixSpeedActual", oldSpeed, item.getMixSpeedActual());
        auditService.recordChange(AUDIT_ITEM, item.getId(), "mixMinutesActual", oldMinutes, item.getMixMinutesActual());
        auditService.recordChange(AUDIT_ITEM, item.getId(), "note", oldNote, item.getNote());

        // Пересчитываем допуск, если у строки есть привязка к рецептуре
        if (item.getRecipeItemId() != null && item.getWeight() != null) {
            recipeItemRepository.findById(item.getRecipeItemId()).ifPresent(recipeItem -> {
                boolean withinTolerance = dosageValidator.isWithinTolerance(
                        recipeItem.getTargetQty(), item.getWeight(),
                        recipeItem.getTolerancePlus(), recipeItem.getToleranceMinus());
                item.setWithinTolerance(withinTolerance);
            });
        }

        item.setCorrectedByOperator(true);
        item.setNeedsReview(false);
        checklistItemRepository.save(item);
    }

    /** Шаг «отклонить»: чек-лист неразборчив/некорректен — печатается заново. */
    @Transactional
    public ProductionChecklist reject(UUID checklistId, String reason) {
        ProductionChecklist checklist = getById(checklistId);
        ChecklistStatus before = checklist.getStatus();
        if (before == ChecklistStatus.CONFIRMED || before == ChecklistStatus.PRODUCTION_COMPLETED) {
            throw new InvalidStateTransitionException("Подтверждённый чек-лист нельзя отклонить.");
        }
        checklist.setStatus(ChecklistStatus.REJECTED);
        checklist.setRejectReason(reason);
        auditService.recordChange(AUDIT_CHECKLIST, checklistId, "status", before, ChecklistStatus.REJECTED);
        auditService.recordChange(AUDIT_CHECKLIST, checklistId, "rejectReason", null, reason);
        return checklistRepository.save(checklist);
    }

    /**
     * Создаёт партию, ретроспективную сессию смешивания и записи взвешивания; списывает остатки
     * лотов сырья. Проблемы со сырьём (просрочен, остаток меньше израсходованного) попадают в
     * {@code warnings} и не блокируют запись: физически сырьё уже использовано, и факт должен
     * быть зафиксирован, а расхождение разобрано.
     */
    private Batch createBatchFromChecklist(ProductionChecklist checklist, List<String> warnings) {
        ProductionOrder order = checklist.getProductionOrder();

        String batchNo = batchNumberService.next();

        Batch batch = Batch.builder()
                .batchNo(batchNo)
                .productionOrder(order)
                .status(BatchStatus.PENDING_QC)
                .operator(checklist.getConfirmedBy())
                .producedAt(Instant.now())
                .build();
        batch = batchRepository.save(batch);

        // Сессия смешивания создаётся ретроспективно — для единой модели прослеживаемости,
        // даже если фактическое смешивание не отслеживалось "в реальном времени".
        MixingSession session = MixingSession.builder()
                .batch(batch)
                .status(MixingSessionStatus.FINISHED)
                .startedAt(checklist.getUploadedAt())
                .finishedAt(Instant.now())
                .build();
        session = mixingSessionRepository.save(session);

        for (ChecklistItem item : checklist.getItems()) {
            // Партия сырья необязательна: на бланке продукта может не быть колонки "партия".
            RawMaterialBatch rmBatch = item.getRawMaterialBatchId() != null
                    ? rawMaterialBatchRepository.findById(item.getRawMaterialBatchId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Партия сырья не найдена: " + item.getRawMaterialBatchId()))
                    : null;

            if (rmBatch != null) {
                applyStockMovement(rmBatch, item.getWeight(), warnings);
            }

            RecipeItem recipeItem = item.getRecipeItemId() != null
                    ? recipeItemRepository.findById(item.getRecipeItemId()).orElse(null)
                    : null;

            WeighingRecord record = WeighingRecord.builder()
                    .mixingSession(session)
                    .recipeItem(recipeItem)
                    .rawMaterialBatch(rmBatch)
                    .targetQty(recipeItem != null ? recipeItem.getTargetQty() : item.getWeight())
                    .actualQty(item.getWeight())
                    .withinTolerance(Boolean.TRUE.equals(item.getWithinTolerance()))
                    .stageNo(recipeItem != null && recipeItem.getStage() != null
                            ? recipeItem.getStage().getStageNo() : null)
                    .mixSpeedActual(item.getMixSpeedActual())
                    .mixMinutesActual(item.getMixMinutesActual())
                    .build();
            weighingRecordRepository.save(record);
        }

        eventPublisher.publishBatchEvent(batch.getId().toString(),
                new MesEvent.BatchStarted(batch.getId(), batch.getBatchNo(), order.getId(), Instant.now()));

        return batch;
    }

    /**
     * Проверка срока годности и списание остатка лота. Остаток не уходит ниже нуля: недостающее
     * количество фиксируется в предупреждении (скорее всего, приход не оформлен или лот указан неверно).
     * Лот без учёта остатка ({@code quantityAvailable == null}) не списывается.
     */
    private void applyStockMovement(RawMaterialBatch lot, BigDecimal used, List<String> warnings) {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        if (lot.getExpiry() != null && lot.getExpiry().isBefore(today)) {
            warnings.add("Партия сырья %s просрочена (срок годности до %s).".formatted(lot.getLotNo(), lot.getExpiry()));
        }

        BigDecimal available = lot.getQuantityAvailable();
        if (available == null || used == null) {
            return;
        }
        BigDecimal remaining = available.subtract(used);
        if (remaining.signum() < 0) {
            warnings.add("Партия сырья %s: израсходовано %s кг при остатке %s кг (не хватает %s кг)."
                    .formatted(lot.getLotNo(), plain(used), plain(available), plain(remaining.negate())));
            remaining = BigDecimal.ZERO;
        }
        if (remaining.compareTo(available) != 0) {
            auditService.recordChange("RAW_MATERIAL_BATCH", lot.getId(), "quantityAvailable", available, remaining);
            lot.setQuantityAvailable(remaining);
            rawMaterialBatchRepository.save(lot);
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Список чек-листов: фильтр по заданию имеет приоритет, затем по статусу, иначе все. */
    @Transactional(readOnly = true)
    public Page<ProductionChecklist> list(ChecklistStatus status, UUID orderId, Pageable pageable) {
        if (orderId != null) {
            return checklistRepository.findByProductionOrderId(orderId, pageable);
        }
        return status != null ? checklistRepository.findByStatus(status, pageable) : checklistRepository.findAll(pageable);
    }

    /** Оригинал загруженного файла из хранилища (для просмотра рядом с распознанными строками). */
    @Transactional(readOnly = true)
    public StoredFile getFile(UUID checklistId) {
        ProductionChecklist checklist = getById(checklistId);
        if (checklist.getImagePath() == null) {
            throw new ResourceNotFoundException("Файл ещё не загружен для чек-листа " + checklistId);
        }
        String contentType = checklist.getFileContentType() != null
                ? checklist.getFileContentType() : "application/octet-stream";
        return new StoredFile(storageService.retrieve(checklist.getImagePath()), contentType,
                "checklist-" + checklistId + "." + FileTypes.extensionFor(contentType));
    }

    /**
     * Нужна ли строке партия сырья. Результат более ранней стадии (полуфабрикат, {@code sourceStageNo})
     * берётся не со склада, поэтому партии сырья у него нет — иначе многостадийный лист нельзя подтвердить.
     */
    private boolean needsRawMaterialLot(Recipe recipe, ChecklistItem item) {
        if (item.getRecipeItemId() == null) {
            return true;
        }
        return recipe.getItems().stream()
                .filter(ri -> item.getRecipeItemId().equals(ri.getId()))
                .findFirst()
                .map(ri -> ri.getSourceStageNo() == null)
                .orElse(true);
    }
}
