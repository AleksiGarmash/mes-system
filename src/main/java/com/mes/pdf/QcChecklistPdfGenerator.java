package com.mes.pdf;

import com.mes.domain.Batch;
import com.mes.domain.QualitySpec;
import com.mes.domain.QualitySpecItem;
import com.mes.domain.enums.QcParameterType;
import com.mes.qr.BatchQrPayload;
import com.mes.qr.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Печатный протокол контроля качества под конкретную партию: заголовок, QR-код партии
 * ({@link BatchQrPayload}), таблица параметров QC-спецификации с допусками (диапазон для
 * числовых параметров, список допустимых значений для качественных) и пустые поля
 * «измерено», «решение по партии», «мероприятия по несоответствиям», две подписи.
 */
@Component
@RequiredArgsConstructor
public class QcChecklistPdfGenerator {

    private static final float QR_SIZE = 90f;
    private static final float COL_PARAM = PdfPageWriter.MARGIN;
    private static final float COL_RANGE = 225f;
    private static final float COL_MEASURED = 395f;

    private final QrCodeService qrCodeService;

    public byte[] generate(Batch batch, QualitySpec qualitySpec) {
        BatchQrPayload payload = new BatchQrPayload(
                batch.getId(), batch.getBatchNo(), batch.getProductionOrder().getRecipe().getCode());
        BufferedImage qrImage = qrCodeService.generate(payload);

        try (PDDocument document = new PDDocument();
             PdfPageWriter w = new PdfPageWriter(document)) {

            PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage);
            w.drawImageTopRight(qr, QR_SIZE);

            w.line("ПРОТОКОЛ КОНТРОЛЯ КАЧЕСТВА", 15, true);
            w.line("Партия №: " + batch.getBatchNo(), 10, false);
            w.line("Продукция: " + batch.getProductionOrder().getRecipe().getName()
                    + " (" + batch.getProductionOrder().getRecipe().getCode() + ")", 10, false);
            w.line("Дата: «____» ______________ 20___ г.", 10, false);
            if (qualitySpec != null) {
                w.line("QC-спецификация: " + qualitySpec.getCode() + " (версия " + qualitySpec.getVersion() + ")", 10, false);
            }
            w.skip(10);

            w.text(COL_PARAM, "Параметр", 9, true, 180);
            w.text(COL_RANGE, "Допуск", 9, true, 160);
            w.text(COL_MEASURED, "Измерено", 9, true, 100);
            w.skip(4);
            w.rule(0.8f);
            w.skip(18);

            List<QualitySpecItem> items = qualitySpec == null ? List.of() : qualitySpec.getItems().stream()
                    .sorted(Comparator.comparing(QualitySpecItem::getSequenceNo,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            for (QualitySpecItem item : items) {
                w.ensureSpace(32);
                String unit = item.getUnit() != null ? " (" + item.getUnit() + ")" : "";
                w.text(COL_PARAM, item.getParameterName() + unit, 10, false, COL_RANGE - COL_PARAM - 8);
                w.text(COL_RANGE, tolerance(item), 9, false, COL_MEASURED - COL_RANGE - 8);
                w.underline(COL_MEASURED, w.y() - 2, 120);
                w.skip(26);
            }

            w.ensureSpace(150);
            w.skip(10);
            w.line("Решение по партии: ________________________________________________", 10, false);
            w.line("Мероприятия по несоответствиям: ______________________________________", 10, false);
            w.skip(14);
            w.line("Ф.И.О. лаборанта: ______________________   Подпись: ______________________", 10, false);
            w.line("Ф.И.О.: ______________________________   Подпись: ______________________", 10, false);

            w.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сформировать PDF протокола QC", e);
        }
    }

    private String tolerance(QualitySpecItem item) {
        if (item.getParameterType() == QcParameterType.QUALITATIVE) {
            return "допустимо: " + (item.getAllowedValues() != null ? item.getAllowedValues() : "—");
        }
        if (item.getMinValue() != null && item.getMaxValue() != null) {
            return plain(item.getMinValue()) + " … " + plain(item.getMaxValue());
        } else if (item.getMinValue() != null) {
            return "≥ " + plain(item.getMinValue());
        } else if (item.getMaxValue() != null) {
            return "≤ " + plain(item.getMaxValue());
        }
        return "—";
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
