package com.mes.pdf;

import com.mes.domain.ProductionOrder;
import com.mes.domain.Recipe;
import com.mes.domain.RecipeControlPoint;
import com.mes.domain.RecipeItem;
import com.mes.domain.RecipeStage;
import com.mes.qr.OrderQrPayload;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Печатный бланк чек-листа производства под конкретное задание: заголовок, QR-код задания
 * ({@link OrderQrPayload}), стадии рецептуры с таблицей компонентов (норма, поля «партия»,
 * «факт», обороты и длительность перемешивания, если они заданы нормой) и контрольные точки
 * с пустыми полями под рукописные замеры.
 * <p>
 * Колонка «Партия сырья» печатается только если рецептура её требует
 * ({@link Recipe#getRequireRawMaterialLot()}). Один универсальный макет без шаблонизации.
 */
@Component
@RequiredArgsConstructor
public class ChecklistPdfGenerator {

    private static final float QR_SIZE = 90f;
    private static final float ROW_HEIGHT = 34f;

    private static final float COL_NAME = PdfPageWriter.MARGIN;
    private static final float COL_PLAN = 215f;
    private static final float COL_LOT = 275f;
    private static final float COL_FACT = 365f;
    private static final float COL_RPM = 435f;
    private static final float COL_MIN = 487f;

    private final QrCodeService qrCodeService;

    public byte[] generate(ProductionOrder order) {
        Recipe recipe = order.getRecipe();
        OrderQrPayload payload = new OrderQrPayload(
                order.getId(), order.getOrderNo(), recipe.getCode(), recipe.getVersion());
        BufferedImage qrImage = qrCodeService.generate(payload);

        boolean showLot = !Boolean.FALSE.equals(recipe.getRequireRawMaterialLot());
        boolean showMix = recipe.getItems().stream()
                .anyMatch(i -> i.getMixSpeedNorm() != null || i.getMixMinutesNorm() != null);

        try (PDDocument document = new PDDocument();
             PdfPageWriter w = new PdfPageWriter(document)) {

            PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage);
            w.drawImageTopRight(qr, QR_SIZE);

            w.line("ПРОИЗВОДСТВЕННОЕ ЗАДАНИЕ №" + order.getOrderNo(), 15, true);
            w.line("Рецептура: " + recipe.getCode() + " (версия " + recipe.getVersion() + "), "
                    + recipe.getName(), 10, false);
            w.line("План: " + plain(order.getPlannedQty()) + " кг", 10, false);
            if (order.getPlannedDate() != null) {
                w.line("Дата: " + DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        .format(order.getPlannedDate().atZone(ZoneOffset.UTC)), 10, false);
            }
            w.line("Партия №: ____________    Время начала: ________    Время окончания: ________", 10, false);
            w.skip(6);

            List<RecipeItem> items = recipe.getItems().stream()
                    .sorted(Comparator.comparing(RecipeItem::getSequenceNo,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            if (recipe.getStages().isEmpty()) {
                writeTable(w, items, showLot, showMix);
            } else {
                for (RecipeStage stage : recipe.getStages()) {
                    w.ensureSpace(80);
                    w.line("Стадия " + stage.getStageNo() + " — " + stage.getName()
                            + (stage.getPlannedQty() != null ? ", " + plain(stage.getPlannedQty()) + " кг" : ""), 11, true);
                    List<RecipeItem> stageItems = items.stream()
                            .filter(i -> i.getStage() != null && stage.getStageNo().equals(i.getStage().getStageNo()))
                            .toList();
                    writeTable(w, stageItems, showLot, showMix);
                    writeControlPoints(w, recipe, stage.getStageNo());
                }
            }

            writeControlPoints(w, recipe, null);

            w.skip(18);
            w.line("Оператор: ______________________   Подпись: ______________________", 10, false);
            w.line("Отметки по ведению процесса (отклонения): ___________________________________", 10, false);

            w.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сформировать PDF чек-листа", e);
        }
    }

    private void writeTable(PdfPageWriter w, List<RecipeItem> items, boolean showLot, boolean showMix) throws IOException {
        writeTableHeader(w, showLot, showMix);
        for (RecipeItem item : items) {
            w.ensureSpace(ROW_HEIGHT + 4);

            String label = item.getMaterialCode() != null ? item.getMaterialCode() : String.valueOf(item.getMaterialId());
            if (item.getSourceStageNo() != null) {
                label += " (результат стадии " + item.getSourceStageNo() + ")";
            }
            w.text(COL_NAME, label, 10, false, COL_PLAN - COL_NAME - 6);
            w.text(COL_PLAN, plain(item.getTargetQty()) + " " + (item.getUnit() != null ? item.getUnit() : "кг"),
                    9, false, 55);

            if (showLot) {
                w.underline(COL_LOT, w.y() - 2, COL_FACT - COL_LOT - 10);
            }
            w.underline(COL_FACT, w.y() - 2, 62);
            if (showMix) {
                w.underline(COL_RPM, w.y() - 2, 44);
                w.underline(COL_MIN, w.y() - 2, 36);
            }

            String norms = mixNorms(item);
            if (!norms.isEmpty()) {
                w.skip(11);
                w.note(COL_NAME, norms, 7.5f, COL_LOT - COL_NAME);
                w.skip(ROW_HEIGHT - 11);
            } else {
                w.skip(ROW_HEIGHT - 8);
            }
        }
        w.skip(4);
    }

    private void writeTableHeader(PdfPageWriter w, boolean showLot, boolean showMix) throws IOException {
        w.ensureSpace(60);
        w.text(COL_NAME, "Компонент", 9, true, 170);
        w.text(COL_PLAN, "План", 9, true, 55);
        if (showLot) {
            w.text(COL_LOT, "Партия сырья", 9, true, 85);
        }
        w.text(COL_FACT, "Факт, кг", 9, true, 65);
        if (showMix) {
            w.text(COL_RPM, "Об/мин", 9, true, 48);
            w.text(COL_MIN, "Мин", 9, true, 40);
        }
        w.skip(4);
        w.rule(0.8f);
        w.skip(18);
    }

    private void writeControlPoints(PdfPageWriter w, Recipe recipe, Integer stageNo) throws IOException {
        List<RecipeControlPoint> points = recipe.getControlPoints().stream()
                .filter(p -> stageNo == null ? p.getStageNo() == null : stageNo.equals(p.getStageNo()))
                .toList();
        if (points.isEmpty()) {
            return;
        }
        w.ensureSpace(40 + 22 * points.size());
        w.line(stageNo == null ? "КОНТРОЛЬ ГОТОВОГО ПРОДУКТА" : "КОНТРОЛЬ", 10, true);
        for (RecipeControlPoint p : points) {
            w.ensureSpace(24);
            w.text(COL_NAME, p.getParameterName() + ":", 10, false, 200);
            w.underline(COL_PLAN + 20, w.y() - 2, 90);
            w.text(COL_PLAN + 120, "(" + range(p) + ")", 9, false, 150);
            w.skip(22);
        }
        w.skip(4);
    }

    private String mixNorms(RecipeItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getMixSpeedNorm() != null) {
            sb.append("обороты: ").append(item.getMixSpeedNorm()).append("; ");
        }
        if (item.getMixMinutesNorm() != null) {
            sb.append("перемешивание: ").append(item.getMixMinutesNorm()).append(" мин; ");
        }
        if (item.getNote() != null) {
            sb.append(item.getNote());
        }
        return sb.toString().trim();
    }

    private String range(RecipeControlPoint p) {
        String unit = p.getUnit() != null ? " " + p.getUnit() : "";
        if (p.getMinValue() != null && p.getMaxValue() != null) {
            return plain(p.getMinValue()) + " – " + plain(p.getMaxValue()) + unit;
        } else if (p.getMinValue() != null) {
            return "не менее " + plain(p.getMinValue()) + unit;
        } else if (p.getMaxValue() != null) {
            return "не более " + plain(p.getMaxValue()) + unit;
        }
        return "—";
    }

    private static String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }
}
