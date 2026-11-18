package com.mes.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Помощник построчного вывода в PDF: переносит содержимое на новую страницу, когда место
 * заканчивается (рецептуры с несколькими стадиями не помещаются на одну страницу), и
 * использует встроенный шрифт DejaVu — стандартные шрифты PDF (Helvetica) не содержат кириллицы.
 */
final class PdfPageWriter implements AutoCloseable {

    static final float MARGIN = 40f;
    private static final float BOTTOM = 60f;

    private final PDDocument document;
    private final PDFont regular;
    private final PDFont bold;

    private PDPageContentStream stream;
    private PDPage page;
    private float y;

    PdfPageWriter(PDDocument document) throws IOException {
        this.document = document;
        this.regular = loadFont(document, "fonts/DejaVuSans.ttf");
        this.bold = loadFont(document, "fonts/DejaVuSans-Bold.ttf");
        newPage();
    }

    private static PDFont loadFont(PDDocument document, String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return PDType0Font.load(document, in);
        }
    }

    float width() {
        return page.getMediaBox().getWidth();
    }

    float y() {
        return y;
    }

    void skip(float points) {
        y -= points;
    }

    void newPage() throws IOException {
        if (stream != null) {
            stream.close();
        }
        page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = page.getMediaBox().getHeight() - MARGIN;
    }

    /** Если до нижнего поля меньше {@code needed} пунктов — начинает новую страницу. */
    void ensureSpace(float needed) throws IOException {
        if (y - needed < BOTTOM) {
            newPage();
        }
    }

    void drawImageTopRight(PDImageXObject image, float size) throws IOException {
        stream.drawImage(image, width() - MARGIN - size, page.getMediaBox().getHeight() - MARGIN - size, size, size);
    }

    /** Текст в текущей строке по горизонтали {@code x}; вертикальная позиция не меняется. */
    void text(float x, String text, float size, boolean useBold, float maxWidth) throws IOException {
        PDFont font = useBold ? bold : regular;
        String safe = fit(sanitize(text), font, size, maxWidth);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(safe);
        stream.endText();
    }

    /** Строка текста от левого поля с переходом на следующую строку. */
    void line(String text, float size, boolean useBold) throws IOException {
        ensureSpace(size + 10);
        text(MARGIN, text, size, useBold, width() - 2 * MARGIN);
        y -= size + 8;
    }

    /** Приглушённый серый текст (нормы, примечания) под основной строкой. */
    void note(float x, String text, float size, float maxWidth) throws IOException {
        stream.setNonStrokingColor(0.35f);
        text(x, text, size, false, maxWidth);
        stream.setNonStrokingColor(0f);
    }

    void rule(float lineWidth) throws IOException {
        stream.setLineWidth(lineWidth);
        stream.moveTo(MARGIN, y);
        stream.lineTo(width() - MARGIN, y);
        stream.stroke();
    }

    void underline(float x, float yPos, float length) throws IOException {
        stream.setLineWidth(0.5f);
        stream.moveTo(x, yPos);
        stream.lineTo(x + length, yPos);
        stream.stroke();
    }

    private static String sanitize(String text) {
        return text == null ? "" : text.replaceAll("[\\r\\n\\t]+", " ");
    }

    /** Обрезает текст многоточием, если он не помещается в {@code maxWidth}. */
    private static String fit(String text, PDFont font, float size, float maxWidth) throws IOException {
        if (maxWidth <= 0 || width(text, font, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        String cut = text;
        while (!cut.isEmpty() && width(cut + ellipsis, font, size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ellipsis;
    }

    private static float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }
}
