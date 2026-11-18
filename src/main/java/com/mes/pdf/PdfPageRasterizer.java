package com.mes.pdf;

import com.mes.exception.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Превращает PDF (в т.ч. многостраничный скан чек-листа) в список изображений —
 * по одному на страницу. После этого шага PDF и обычное фото обрабатываются
 * дальше по единому пайплайну (поиск QR, распознавание полей).
 * <p>
 * Число страниц ограничено ({@link #MAX_PAGES}): страница A4 на 300 DPI занимает около 35 МБ,
 * и без ограничения «PDF-бомба» исчерпала бы память сервера.
 */
@Component
public class PdfPageRasterizer {

    private static final float DPI = 300f; // 300 DPI — достаточно для чёткого распознавания текста
    static final int MAX_PAGES = 10;

    /** @return изображения всех страниц PDF, в порядке следования */
    public List<BufferedImage> rasterize(byte[] pdfBytes) {
        List<BufferedImage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            checkPageLimit(document.getNumberOfPages());
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                pages.add(renderer.renderImageWithDPI(i, DPI, ImageType.RGB));
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось растеризовать PDF чек-листа", e);
        }
        return pages;
    }

    /** Число страниц без растеризации: для PDF читается из документа, для фото всегда 1. */
    public int countPages(byte[] fileBytes, String contentType) {
        if (!isPdf(contentType, fileBytes)) {
            return 1;
        }
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            int pages = document.getNumberOfPages();
            checkPageLimit(pages);
            return pages;
        } catch (IOException e) {
            throw new InvalidRequestException("Файл не является корректным PDF: " + e.getMessage());
        }
    }

    private void checkPageLimit(int pages) {
        if (pages > MAX_PAGES) {
            throw new InvalidRequestException(
                    "В документе %d страниц, допустимо не более %d.".formatted(pages, MAX_PAGES));
        }
    }

    public boolean isPdf(String contentType, byte[] fileBytes) {
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        // fallback: сигнатура PDF-файла ("%PDF")
        return fileBytes.length > 4
                && fileBytes[0] == '%' && fileBytes[1] == 'P' && fileBytes[2] == 'D' && fileBytes[3] == 'F';
    }
}
