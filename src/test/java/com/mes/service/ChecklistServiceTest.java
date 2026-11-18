package com.mes.service;

import com.mes.domain.*;
import com.mes.domain.enums.ChecklistStatus;
import com.mes.domain.enums.RecipeStatus;
import com.mes.event.MesEventPublisher;
import com.mes.ocr.*;
import com.mes.pdf.PdfPageRasterizer;
import com.mes.qr.QrCodeService;
import com.mes.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    @Mock private ProductionChecklistRepository checklistRepository;
    @Mock private ChecklistItemRepository checklistItemRepository;
    @Mock private ProductionOrderRepository orderRepository;
    @Mock private RecipeItemRepository recipeItemRepository;
    @Mock private RawMaterialBatchRepository rawMaterialBatchRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private MixingSessionRepository mixingSessionRepository;
    @Mock private WeighingRecordRepository weighingRecordRepository;
    @Mock private OcrEngine ocrEngine;
    @Mock private ChecklistStorageService storageService;
    @Mock private MesEventPublisher eventPublisher;
    @Mock private QrCodeService qrCodeService;
    @Mock private PdfPageRasterizer pdfPageRasterizer;
    @Mock private AuditService auditService;
    @Mock private BatchNumberService batchNumberService;

    private DosageValidator dosageValidator;
    private ChecklistService checklistService;

    private Recipe recipe;
    private ProductionChecklist checklist;

    /** Простое тестовое изображение — содержимое неважно, OCR замокан, а QR-декодер тоже. */
    private static final byte[] FAKE_IMAGE_BYTES = encodeBlankPng();

    @BeforeEach
    void setUp() {
        dosageValidator = new DosageValidator();
        checklistService = new ChecklistService(
                checklistRepository, checklistItemRepository, orderRepository, recipeItemRepository,
                rawMaterialBatchRepository, batchRepository, mixingSessionRepository, weighingRecordRepository,
                ocrEngine, storageService, dosageValidator, eventPublisher, qrCodeService, pdfPageRasterizer,
                new RangeValidator(), auditService, batchNumberService);

        RecipeItem sandItem = RecipeItem.builder()
                .id(UUID.randomUUID())
                .materialId(UUID.randomUUID())
                .materialCode("Песок")
                .targetQty(BigDecimal.valueOf(750))
                .tolerancePlus(BigDecimal.valueOf(2))
                .toleranceMinus(BigDecimal.valueOf(2))
                .build();

        recipe = Recipe.builder()
                .id(UUID.randomUUID())
                .code("SM-17")
                .name("Формовочная смесь")
                .version(1)
                .status(RecipeStatus.ACTIVE)
                .items(List.of(sandItem))
                .build();

        ProductionOrder order = ProductionOrder.builder()
                .id(UUID.randomUUID())
                .orderNo("ORD-1")
                .recipe(recipe)
                .build();

        checklist = ProductionChecklist.builder()
                .id(UUID.randomUUID())
                .productionOrder(order)
                .status(ChecklistStatus.UPLOADED)
                .imagePath("checklists/test.png")
                .fileSha256(com.mes.util.FileHashing.sha256Hex(FAKE_IMAGE_BYTES))
                .fileContentType("image/png")
                .build();
        lenient().when(storageService.retrieve("checklists/test.png")).thenReturn(FAKE_IMAGE_BYTES);
        lenient().when(batchNumberService.next()).thenReturn("B-2026-000001");

        lenient().when(checklistRepository.findById(checklist.getId())).thenReturn(Optional.of(checklist));
        lenient().when(checklistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // По умолчанию: файл — не PDF (обычное фото), и QR на нём не найден (старый бланк без QR)
        lenient().when(pdfPageRasterizer.isPdf(anyString(), any())).thenReturn(false);
        lenient().when(qrCodeService.decode(any(BufferedImage.class), eq(com.mes.qr.OrderQrPayload.class))).thenReturn(Optional.empty());
    }

    private static byte[] encodeBlankPng() {
        try {
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void recognize_withinToleranceAndMatchedMaterial_doesNotNeedReview() {
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Песок", 0.98),
                new OcrField<>("RM-1234", 0.95),
                new OcrField<>(BigDecimal.valueOf(749.4), 0.99));

        when(ocrEngine.recognize(any())).thenReturn(
                new OcrDocumentResult(new OcrField<>("245", 0.9), new OcrField<>("SM-17", 0.9), List.of(line), 0.97));
        when(rawMaterialBatchRepository.findByLotNo("RM-1234"))
                .thenReturn(Optional.of(RawMaterialBatch.builder().id(UUID.randomUUID()).lotNo("RM-1234").build()));

        ProductionChecklist result = checklistService.recognize(checklist.getId());

        assertEquals(ChecklistStatus.WAITING_CONFIRMATION, result.getStatus());
        assertEquals(1, result.getItems().size());
        ChecklistItem item = result.getItems().get(0);
        assertFalse(item.isNeedsReview());
        assertTrue(item.getWithinTolerance());
    }

    @Test
    void recognize_outOfToleranceWeight_flagsForReview() {
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Песок", 0.98),
                new OcrField<>("RM-1234", 0.95),
                new OcrField<>(BigDecimal.valueOf(900), 0.99)); // target 750 +-2% -> way out

        when(ocrEngine.recognize(any())).thenReturn(
                new OcrDocumentResult(new OcrField<>("245", 0.9), new OcrField<>("SM-17", 0.9), List.of(line), 0.97));
        when(rawMaterialBatchRepository.findByLotNo("RM-1234"))
                .thenReturn(Optional.of(RawMaterialBatch.builder().id(UUID.randomUUID()).lotNo("RM-1234").build()));

        ProductionChecklist result = checklistService.recognize(checklist.getId());

        ChecklistItem item = result.getItems().get(0);
        assertTrue(item.isNeedsReview());
        assertFalse(item.getWithinTolerance());
    }

    @Test
    void recognize_lowConfidenceField_flagsForReview() {
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Песок", 0.5), // low confidence
                new OcrField<>("RM-1234", 0.95),
                new OcrField<>(BigDecimal.valueOf(750), 0.99));

        when(ocrEngine.recognize(any())).thenReturn(
                new OcrDocumentResult(new OcrField<>("245", 0.9), new OcrField<>("SM-17", 0.9), List.of(line), 0.8));
        when(rawMaterialBatchRepository.findByLotNo("RM-1234"))
                .thenReturn(Optional.of(RawMaterialBatch.builder().id(UUID.randomUUID()).lotNo("RM-1234").build()));

        ProductionChecklist result = checklistService.recognize(checklist.getId());

        assertTrue(result.getItems().get(0).isNeedsReview());
    }

    @Test
    void recognize_unmatchedMaterial_flagsForReview() {
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Неизвестный материал", 0.99),
                new OcrField<>("RM-1234", 0.95),
                new OcrField<>(BigDecimal.valueOf(750), 0.99));

        when(ocrEngine.recognize(any())).thenReturn(
                new OcrDocumentResult(new OcrField<>("245", 0.9), new OcrField<>("SM-17", 0.9), List.of(line), 0.97));

        ProductionChecklist result = checklistService.recognize(checklist.getId());

        ChecklistItem item = result.getItems().get(0);
        assertTrue(item.isNeedsReview());
        assertNull(item.getMaterialId());
    }

    @Test
    void recognize_qrMatchesOrder_setsQrVerifiedTrue() {
        var payload = new com.mes.qr.OrderQrPayload(
                checklist.getProductionOrder().getId(), "ORD-1", "SM-17", 1);
        when(qrCodeService.decode(any(BufferedImage.class), eq(com.mes.qr.OrderQrPayload.class))).thenReturn(Optional.of(payload));
        when(ocrEngine.recognize(any())).thenReturn(
                new OcrDocumentResult(null, null, List.of(), 1.0));

        ProductionChecklist result = checklistService.recognize(checklist.getId());

        assertTrue(result.getQrVerified());
    }

    @Test
    void recognize_qrBelongsToDifferentOrder_throws() {
        var payload = new com.mes.qr.OrderQrPayload(UUID.randomUUID(), "ORD-999", "OTHER", 1);
        when(qrCodeService.decode(any(BufferedImage.class), eq(com.mes.qr.OrderQrPayload.class))).thenReturn(Optional.of(payload));

        assertThrows(com.mes.exception.QrVerificationException.class, () ->
                checklistService.recognize(checklist.getId()));
    }

    // ---------- модель под реальные бланки ----------

    @Test
    void recognize_componentMissingOnSheet_addsPlaceholderRowForManualEntry() {
        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(), 0.0));

        ProductionChecklist result = checklistService.recognize(checklist.getId());

        assertEquals(1, result.getItems().size());
        ChecklistItem placeholder = result.getItems().get(0);
        assertTrue(placeholder.isNeedsReview());
        assertNull(placeholder.getWeight());
        assertEquals(recipe.getItems().get(0).getId(), placeholder.getRecipeItemId());
    }

    @Test
    void recognize_stageMassDiffersFromPlan_setsReviewSummary() {
        RecipeStage stage = RecipeStage.builder()
                .id(UUID.randomUUID()).stageNo(1).name("Стадия 1").plannedQty(BigDecimal.valueOf(1000)).build();
        recipe.getStages().add(stage);
        recipe.getItems().get(0).setStage(stage);

        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Песок", 0.98), new OcrField<>("RM-1", 0.95), new OcrField<>(BigDecimal.valueOf(500), 0.99));
        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(line), 0.97));
        when(rawMaterialBatchRepository.findByLotNo("RM-1")).thenReturn(Optional.empty());

        ProductionChecklist result = checklistService.recognize(checklist.getId());

        assertNotNull(result.getReviewSummary());
        assertTrue(result.getReviewSummary().contains("Стадия 1"));
    }

    private ChecklistItem confirmableItem(UUID lotId) {
        RecipeItem recipeItem = recipe.getItems().get(0);
        ChecklistItem item = ChecklistItem.builder()
                .id(UUID.randomUUID())
                .checklist(checklist)
                .recipeItemId(recipeItem.getId())
                .materialId(recipeItem.getMaterialId())
                .rawMaterialBatchId(lotId)
                .weight(BigDecimal.valueOf(750))
                .withinTolerance(true)
                .build();
        checklist.getItems().add(item);
        return item;
    }

    private void stubBatchCreation() {
        when(batchRepository.save(any())).thenAnswer(inv -> {
            Batch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });
        when(mixingSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(weighingRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recipeItemRepository.findById(recipe.getItems().get(0).getId()))
                .thenReturn(Optional.of(recipe.getItems().get(0)));
    }

    @Test
    void confirm_emptyChecklist_isRejected() {
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class, () ->
                checklistService.confirm(checklist.getId(),
                        new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null)));
    }

    @Test
    void confirm_lotNotRequiredByRecipe_createsBatchWithoutRawMaterialLot() {
        recipe.setRequireRawMaterialLot(false);
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        confirmableItem(null);
        stubBatchCreation();

        ProductionChecklist result = checklistService.confirm(checklist.getId(),
                new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null));

        assertEquals(ChecklistStatus.PRODUCTION_COMPLETED, result.getStatus());
        org.mockito.ArgumentCaptor<WeighingRecord> captor = org.mockito.ArgumentCaptor.forClass(WeighingRecord.class);
        verify(weighingRecordRepository).save(captor.capture());
        assertNull(captor.getValue().getRawMaterialBatch());
    }

    @Test
    void confirm_lotRequiredButMissing_isRejected() {
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        confirmableItem(null);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class, () ->
                checklistService.confirm(checklist.getId(),
                        new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null)));
    }

    private RecipeControlPoint finishedProductPoint() {
        RecipeControlPoint point = RecipeControlPoint.builder()
                .id(UUID.randomUUID())
                .kind(com.mes.domain.enums.ControlPointKind.FINISHED_PRODUCT)
                .parameterName("pH готового продукта")
                .minValue(new BigDecimal("10.0"))
                .maxValue(new BigDecimal("10.5"))
                .build();
        recipe.getControlPoints().add(point);
        return point;
    }

    @Test
    void confirm_controlPointValueMissing_isRejected() {
        recipe.setRequireRawMaterialLot(false);
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        confirmableItem(null);
        finishedProductPoint();

        assertThrows(com.mes.exception.InvalidStateTransitionException.class, () ->
                checklistService.confirm(checklist.getId(),
                        new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null)));
    }

    @Test
    void confirm_finishedProductControlPointOutOfRange_quarantinesBatch() {
        recipe.setRequireRawMaterialLot(false);
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        confirmableItem(null);
        RecipeControlPoint point = finishedProductPoint();
        stubBatchCreation();

        checklistService.confirm(checklist.getId(), new com.mes.dto.request.ConfirmChecklistRequest(
                "Иванов И.И.", null,
                List.of(new com.mes.dto.request.ControlValueRequest(point.getId(), new BigDecimal("11.2")))));

        org.mockito.ArgumentCaptor<Batch> captor = org.mockito.ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository, atLeastOnce()).save(captor.capture());
        assertEquals(com.mes.domain.enums.BatchStatus.QUARANTINE, captor.getValue().getStatus());
        assertEquals(1, checklist.getControlRecords().size());
        assertFalse(checklist.getControlRecords().get(0).isWithinRange());
    }

    // ---------- целостность файла, повторная загрузка, аудит ----------

    @Test
    void recognize_storedFileDiffersFromUploaded_isRejected() {
        when(storageService.retrieve("checklists/test.png")).thenReturn(new byte[]{1, 2, 3});

        assertThrows(com.mes.exception.InvalidStateTransitionException.class,
                () -> checklistService.recognize(checklist.getId()));
        verify(ocrEngine, never()).recognize(any());
    }

    @Test
    void upload_computesHashStoresFileAndResetsPreviousRecognition() {
        checklist.getItems().add(ChecklistItem.builder().id(UUID.randomUUID()).checklist(checklist).build());
        byte[] file = new byte[]{9, 9, 9};
        when(storageService.store(checklist.getId(), file, "application/pdf")).thenReturn("checklists/new.pdf");
        when(pdfPageRasterizer.countPages(file, "application/pdf")).thenReturn(2);

        ProductionChecklist result = checklistService.upload(checklist.getId(), file, "application/pdf", "Иванов");

        assertEquals("checklists/new.pdf", result.getImagePath());
        assertEquals(com.mes.util.FileHashing.sha256Hex(file), result.getFileSha256());
        assertEquals("application/pdf", result.getFileContentType());
        assertEquals(2, result.getPageCount());
        assertTrue(result.getItems().isEmpty());
        assertEquals(ChecklistStatus.UPLOADED, result.getStatus());
    }

    @Test
    void upload_afterConfirmation_isRejected() {
        checklist.setStatus(ChecklistStatus.PRODUCTION_COMPLETED);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class,
                () -> checklistService.upload(checklist.getId(), new byte[]{1}, "image/png", "Иванов"));
        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void upload_sameFileAlreadyUsedByAnotherChecklist_isRejected() {
        byte[] file = new byte[]{5, 5};
        ProductionChecklist other = ProductionChecklist.builder().id(UUID.randomUUID()).build();
        when(checklistRepository.findFirstByFileSha256(com.mes.util.FileHashing.sha256Hex(file)))
                .thenReturn(Optional.of(other));

        assertThrows(com.mes.exception.InvalidRequestException.class,
                () -> checklistService.upload(checklist.getId(), file, "image/png", "Иванов"));
        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void reject_confirmedChecklist_isRejected() {
        checklist.setStatus(ChecklistStatus.PRODUCTION_COMPLETED);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class,
                () -> checklistService.reject(checklist.getId(), "ошибка"));
    }

    @Test
    void confirm_operatorCorrection_isWrittenToAuditLog() {
        recipe.setRequireRawMaterialLot(false);
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        ChecklistItem item = confirmableItem(null);
        stubBatchCreation();

        checklistService.confirm(checklist.getId(), new com.mes.dto.request.ConfirmChecklistRequest(
                "Иванов И.И.",
                List.of(new com.mes.dto.request.ConfirmChecklistItemRequest(
                        item.getId(), null, null, BigDecimal.valueOf(748)))));

        verify(auditService).recordChange(ChecklistService.AUDIT_ITEM, item.getId(), "weight",
                BigDecimal.valueOf(750), BigDecimal.valueOf(748));
        verify(auditService).recordStatus(ChecklistService.AUDIT_CHECKLIST, checklist.getId(),
                ChecklistStatus.WAITING_CONFIRMATION, ChecklistStatus.CONFIRMED);
    }

    @Test
    void confirm_usesBatchNumberFromSequenceService() {
        recipe.setRequireRawMaterialLot(false);
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        confirmableItem(null);
        stubBatchCreation();

        checklistService.confirm(checklist.getId(), new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null));

        org.mockito.ArgumentCaptor<Batch> captor = org.mockito.ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository, atLeastOnce()).save(captor.capture());
        assertEquals("B-2026-000001", captor.getValue().getBatchNo());
    }

    // ---------- склад: списание остатка, срок годности ----------

    private RawMaterialBatch lotWith(String qty, java.time.LocalDate expiry) {
        RawMaterialBatch lot = RawMaterialBatch.builder().id(UUID.randomUUID()).lotNo("RM-77")
                .quantityAvailable(qty == null ? null : new BigDecimal(qty)).expiry(expiry).build();
        when(rawMaterialBatchRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        return lot;
    }

    private static final java.time.LocalDate FAR = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(200);

    @Test
    void confirm_decrementsLotStockByUsedWeightAndLogsIt() {
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        RawMaterialBatch lot = lotWith("1000", FAR);
        confirmableItem(lot.getId()); // вес 750
        stubBatchCreation();

        ProductionChecklist result = checklistService.confirm(checklist.getId(),
                new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null));

        assertEquals(0, new BigDecimal("250").compareTo(lot.getQuantityAvailable()));
        verify(auditService).recordChange("RAW_MATERIAL_BATCH", lot.getId(), "quantityAvailable",
                new BigDecimal("1000"), new BigDecimal("250"));
        assertNull(result.getReviewSummary());
    }

    @Test
    void confirm_stockShorterThanUsed_clampsToZeroAndWarnsWithoutBlocking() {
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        RawMaterialBatch lot = lotWith("700", FAR);
        confirmableItem(lot.getId()); // вес 750 > остатка 700
        stubBatchCreation();

        ProductionChecklist result = checklistService.confirm(checklist.getId(),
                new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null));

        assertEquals(ChecklistStatus.PRODUCTION_COMPLETED, result.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(lot.getQuantityAvailable()));
        assertNotNull(result.getReviewSummary());
        assertTrue(result.getReviewSummary().contains("RM-77"));
        assertTrue(result.getReviewSummary().contains("не хватает 50"));
    }

    @Test
    void confirm_expiredLot_warnsButStillRecordsFact() {
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        RawMaterialBatch lot = lotWith("2000", java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(3));
        confirmableItem(lot.getId());
        stubBatchCreation();

        ProductionChecklist result = checklistService.confirm(checklist.getId(),
                new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null));

        assertEquals(ChecklistStatus.PRODUCTION_COMPLETED, result.getStatus());
        assertTrue(result.getReviewSummary().contains("просрочена"));
    }

    @Test
    void confirm_lotWithoutStockTracking_isNotDecrementedAndGivesNoWarning() {
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        RawMaterialBatch lot = lotWith(null, FAR);
        confirmableItem(lot.getId());
        stubBatchCreation();

        ProductionChecklist result = checklistService.confirm(checklist.getId(),
                new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null));

        assertNull(lot.getQuantityAvailable());
        assertNull(result.getReviewSummary());
        verify(rawMaterialBatchRepository, never()).save(any());
    }

    @Test
    void confirm_intermediateProductFromEarlierStage_doesNotRequireRawMaterialLot() {
        // регрессия: строка «результат стадии 1» не имеет лота сырья, а рецептура требует лоты
        RecipeItem intermediate = RecipeItem.builder()
                .id(UUID.randomUUID()).materialId(UUID.randomUUID()).materialCode("Раствор")
                .targetQty(BigDecimal.valueOf(300)).tolerancePlus(BigDecimal.ONE).toleranceMinus(BigDecimal.ZERO)
                .sourceStageNo(1).build();
        recipe.setItems(new java.util.ArrayList<>(List.of(recipe.getItems().get(0), intermediate)));
        recipe.setRequireRawMaterialLot(true);
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        RawMaterialBatch lot = RawMaterialBatch.builder().id(UUID.randomUUID()).lotNo("RM-1").build();
        when(rawMaterialBatchRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        confirmableItem(lot.getId());
        checklist.getItems().add(ChecklistItem.builder()
                .id(UUID.randomUUID()).checklist(checklist)
                .recipeItemId(intermediate.getId()).materialId(intermediate.getMaterialId())
                .weight(BigDecimal.valueOf(301)).withinTolerance(true).build());
        stubBatchCreation();
        when(recipeItemRepository.findById(intermediate.getId())).thenReturn(Optional.of(intermediate));

        ProductionChecklist result = checklistService.confirm(checklist.getId(),
                new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null));

        assertEquals(ChecklistStatus.PRODUCTION_COMPLETED, result.getStatus());
    }

    @Test
    void confirm_regularComponentWithoutLot_whenLotRequired_isStillRejected() {
        recipe.setRequireRawMaterialLot(true);
        checklist.setStatus(ChecklistStatus.WAITING_CONFIRMATION);
        confirmableItem(null);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class, () ->
                checklistService.confirm(checklist.getId(),
                        new com.mes.dto.request.ConfirmChecklistRequest("Иванов И.И.", null)));
    }
}
