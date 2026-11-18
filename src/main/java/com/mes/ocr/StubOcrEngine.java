package com.mes.ocr;

import java.math.BigDecimal;
import java.util.List;

/**
 * Заглушка OCR для локальной разработки и тестов, пока не подключён реальный движок
 * (Tesseract/Tess4J, облачный OCR-провайдер или кастомная модель).
 * Регистрируется в {@link OcrConfig}, когда {@code mes.ocr.engine=stub} (значение по умолчанию).
 * В продакшене должна быть заменена реальной реализацией.
 */
public class StubOcrEngine implements OcrEngine {

    @Override
    public OcrDocumentResult recognize(byte[] imageBytes) {
        // Заглушка: в реальной интеграции здесь вызов Tesseract/облачного OCR
        // и разбор результата в структуру OcrDocumentResult.
        return new OcrDocumentResult(
                new OcrField<>("UNKNOWN", 0.0),
                new OcrField<>("UNKNOWN", 0.0),
                List.of(),
                0.0
        );
    }
}
