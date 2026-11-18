package com.mes.service;

import com.mes.domain.*;
import com.mes.domain.enums.QcChecklistStatus;
import com.mes.domain.enums.QualitySpecStatus;
import com.mes.ocr.*;
import com.mes.pdf.PdfPageRasterizer;
import com.mes.qr.BatchQrPayload;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QcChecklistServiceTest {

    @Mock private QcChecklistRepository checklistRepository;
    @Mock private QcChecklistItemRepository checklistItemRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private QualitySpecRepository qualitySpecRepository;
    @Mock private QualitySpecItemRepository qualitySpecItemRepository;
    @Mock private QualityCheckRepository qualityCheckRepository;
    @Mock private OcrEngine ocrEngine;
    @Mock private ChecklistStorageService storageService;
    @Mock private QualityControlService qualityControlService;
    @Mock private QrCodeService qrCodeService;
    @Mock private PdfPageRasterizer pdfPageRasterizer;
    @Mock private AuditService auditService;

    private RangeValidator rangeValidator;
    private QcChecklistService qcChecklistService;

    private QualitySpec spec;
    private QcChecklist checklist;
    private Batch batch;

    private static final byte[] FAKE_IMAGE_BYTES = encodeBlankPng();

    @BeforeEach
    void setUp() {
        rangeValidator = new RangeValidator();
        qcChecklistService = new QcChecklistService(
                checklistRepository, checklistItemRepository, batchRepository, qualitySpecRepository,
                qualitySpecItemRepository, qualityCheckRepository, ocrEngine, storageService,
                rangeValidator, qualityControlService, qrCodeService, pdfPageRasterizer, auditService);

        QualitySpecItem moistureItem = QualitySpecItem.builder()
                .id(UUID.randomUUID())
                .parameterCode("Влажность")
                .parameterName("Влажность")
                .minValue(BigDecimal.valueOf(3.5))
                .maxValue(BigDecimal.valueOf(4.5))
                .unit("%")
                .build();

        spec = QualitySpec.builder()
                .id(UUID.randomUUID())
                .code("QC-SM17")
                .name("QC формовочной смеси")
                .version(1)
                .status(QualitySpecStatus.ACTIVE)
                .items(new java.util.ArrayList<>(List.of(moistureItem)))
                .build();

        Recipe recipe = Recipe.builder().id(UUID.randomUUID()).code("SM-17").qualitySpecCode("QC-SM17").build();
        ProductionOrder order = ProductionOrder.builder().id(UUID.randomUUID()).orderNo("ORD-1").recipe(recipe).build();
        batch = Batch.builder().id(UUID.randomUUID()).batchNo("B-2026-000001").productionOrder(order).build();

        checklist = QcChecklist.builder()
                .id(UUID.randomUUID())
                .batch(batch)
                .qualitySpec(spec)
                .status(QcChecklistStatus.UPLOADED)
                .imagePath("checklists/qc.png")
                .fileSha256(com.mes.util.FileHashing.sha256Hex(FAKE_IMAGE_BYTES))
                .fileContentType("image/png")
                .build();
        lenient().when(storageService.retrieve("checklists/qc.png")).thenReturn(FAKE_IMAGE_BYTES);

        lenient().when(checklistRepository.findById(checklist.getId())).thenReturn(Optional.of(checklist));
        lenient().when(checklistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(pdfPageRasterizer.isPdf(anyString(), any())).thenReturn(false);
        lenient().when(qrCodeService.decode(any(BufferedImage.class), eq(BatchQrPayload.class))).thenReturn(Optional.empty());
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
    void recognize_withinRangeAndMatchedParameter_doesNotNeedReview() {
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Влажность", 0.98),
                null,
                new OcrField<>(BigDecimal.valueOf(4.1), 0.97));

        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(line), 0.97));

        QcChecklist result = qcChecklistService.recognize(checklist.getId());

        assertEquals(QcChecklistStatus.WAITING_CONFIRMATION, result.getStatus());
        QcChecklistItem item = result.getItems().get(0);
        assertFalse(item.isNeedsReview());
        assertTrue(item.getWithinRange());
    }

    @Test
    void recognize_outOfRangeValue_flagsForReview() {
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Влажность", 0.98),
                null,
                new OcrField<>(BigDecimal.valueOf(6.0), 0.97)); // вне [3.5, 4.5]

        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(line), 0.97));

        QcChecklist result = qcChecklistService.recognize(checklist.getId());

        QcChecklistItem item = result.getItems().get(0);
        assertTrue(item.isNeedsReview());
        assertFalse(item.getWithinRange());
    }

    @Test
    void recognize_unmatchedParameter_flagsForReview() {
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Неизвестный параметр", 0.99),
                null,
                new OcrField<>(BigDecimal.valueOf(4.0), 0.97));

        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(line), 0.97));

        QcChecklist result = qcChecklistService.recognize(checklist.getId());

        QcChecklistItem item = result.getItems().get(0);
        assertTrue(item.isNeedsReview());
        assertNull(item.getQualitySpecItemId());
    }

    @Test
    void recognize_batchQrMismatch_throws() {
        var payload = new BatchQrPayload(UUID.randomUUID(), "B-OTHER", "OTHER");
        when(qrCodeService.decode(any(BufferedImage.class), eq(BatchQrPayload.class))).thenReturn(Optional.of(payload));

        assertThrows(com.mes.exception.QrVerificationException.class, () ->
                qcChecklistService.recognize(checklist.getId()));
    }

    @Test
    void confirm_createsQualityChecksAndUpdatesBatchStatus() {
        checklist.setStatus(QcChecklistStatus.WAITING_CONFIRMATION);
        QcChecklistItem item = QcChecklistItem.builder()
                .id(UUID.randomUUID())
                .checklist(checklist)
                .qualitySpecItemId(spec.getItems().get(0).getId())
                .parameterNameRaw("Влажность")
                .measuredValue(BigDecimal.valueOf(4.1))
                .withinRange(true)
                .needsReview(false)
                .build();
        checklist.getItems().add(item);

        lenient().when(qualitySpecItemRepository.findById(item.getQualitySpecItemId())).thenReturn(Optional.of(spec.getItems().get(0)));

        qcChecklistService.confirm(checklist.getId(), new com.mes.dto.request.ConfirmQcChecklistRequest("Петрова А.А.", null));

        verify(qualityCheckRepository, times(1)).save(any());
        verify(qualityControlService, times(1)).updateBatchStatus(batch);
        assertEquals(QcChecklistStatus.CONFIRMED, checklist.getStatus());
    }

    // ---------- качественные параметры, пропущенные строки, решение по партии ----------

    private QualitySpecItem addAppearanceItem() {
        QualitySpecItem appearance = QualitySpecItem.builder()
                .id(UUID.randomUUID())
                .parameterCode("Внешний вид")
                .parameterName("Внешний вид")
                .parameterType(com.mes.domain.enums.QcParameterType.QUALITATIVE)
                .allowedValues("однородная жидкость;однородная жидкость без осадка")
                .build();
        spec.getItems().add(appearance);
        return appearance;
    }

    @Test
    void recognize_qualitativeValueAllowed_doesNotNeedReview() {
        addAppearanceItem();
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Внешний вид", 0.98),
                new OcrField<>("Однородная жидкость", 0.97),
                null);
        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(line), 0.97));

        QcChecklist result = qcChecklistService.recognize(checklist.getId());

        QcChecklistItem item = result.getItems().get(0);
        assertFalse(item.isNeedsReview());
        assertTrue(item.getWithinRange());
    }

    @Test
    void recognize_qualitativeValueNotAllowed_flagsForReview() {
        addAppearanceItem();
        OcrLineItem line = new OcrLineItem(
                new OcrField<>("Внешний вид", 0.98),
                new OcrField<>("мутная с осадком", 0.97),
                null);
        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(line), 0.97));

        QcChecklist result = qcChecklistService.recognize(checklist.getId());

        QcChecklistItem item = result.getItems().get(0);
        assertTrue(item.isNeedsReview());
        assertFalse(item.getWithinRange());
    }

    @Test
    void recognize_parameterMissingOnSheet_addsPlaceholderRow() {
        when(ocrEngine.recognize(any())).thenReturn(new OcrDocumentResult(null, null, List.of(), 0.0));

        QcChecklist result = qcChecklistService.recognize(checklist.getId());

        assertEquals(1, result.getItems().size());
        assertTrue(result.getItems().get(0).isNeedsReview());
        assertNull(result.getItems().get(0).getMeasuredValue());
    }

    @Test
    void confirm_emptyProtocol_isRejected() {
        checklist.setStatus(QcChecklistStatus.WAITING_CONFIRMATION);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class, () ->
                qcChecklistService.confirm(checklist.getId(),
                        new com.mes.dto.request.ConfirmQcChecklistRequest("Петрова А.А.", null)));
    }

    @Test
    void confirm_qualitativeItem_savesTextValueAndDecisionFields() {
        QualitySpecItem appearance = addAppearanceItem();
        checklist.setStatus(QcChecklistStatus.WAITING_CONFIRMATION);
        checklist.getItems().add(QcChecklistItem.builder()
                .id(UUID.randomUUID())
                .checklist(checklist)
                .qualitySpecItemId(appearance.getId())
                .parameterNameRaw("Внешний вид")
                .textValue("однородная жидкость")
                .withinRange(true)
                .build());

        qcChecklistService.confirm(checklist.getId(), new com.mes.dto.request.ConfirmQcChecklistRequest(
                "Петрова А.А.", null, "Партию выпустить", null, "Сидоров П.П."));

        org.mockito.ArgumentCaptor<QualityCheck> captor = org.mockito.ArgumentCaptor.forClass(QualityCheck.class);
        verify(qualityCheckRepository).save(captor.capture());
        assertEquals("однородная жидкость", captor.getValue().getTextValue());
        assertNull(captor.getValue().getMeasuredValue());
        assertEquals(com.mes.domain.enums.QcResult.PASS, captor.getValue().getResult());
        assertEquals("Партию выпустить", checklist.getDecisionNote());
        assertEquals("Сидоров П.П.", checklist.getSecondSignatory());
    }

    // ---------- целостность файла и аудит ----------

    @Test
    void recognize_storedFileDiffersFromUploaded_isRejected() {
        when(storageService.retrieve("checklists/qc.png")).thenReturn(new byte[]{7});

        assertThrows(com.mes.exception.InvalidStateTransitionException.class,
                () -> qcChecklistService.recognize(checklist.getId()));
        verify(ocrEngine, never()).recognize(any());
    }

    @Test
    void upload_afterConfirmation_isRejected() {
        checklist.setStatus(QcChecklistStatus.CONFIRMED);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class,
                () -> qcChecklistService.upload(checklist.getId(), new byte[]{1}, "image/png", "Петрова"));
    }

    @Test
    void upload_duplicateFileOfAnotherProtocol_isRejected() {
        byte[] file = new byte[]{4, 4};
        QcChecklist other = QcChecklist.builder().id(UUID.randomUUID()).build();
        when(checklistRepository.findFirstByFileSha256(com.mes.util.FileHashing.sha256Hex(file)))
                .thenReturn(Optional.of(other));

        assertThrows(com.mes.exception.InvalidRequestException.class,
                () -> qcChecklistService.upload(checklist.getId(), file, "image/png", "Петрова"));
    }

    @Test
    void reject_confirmedProtocol_isRejected() {
        checklist.setStatus(QcChecklistStatus.CONFIRMED);

        assertThrows(com.mes.exception.InvalidStateTransitionException.class,
                () -> qcChecklistService.reject(checklist.getId(), "ошибка"));
    }
}
