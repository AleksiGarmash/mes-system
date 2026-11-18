package com.mes.ocr;

/**
 * Абстракция над движком распознавания. Реализации:
 * - {@code TesseractOcrEngine} — локальный Tesseract (Tess4J) для типовых бланков;
 * - облачный OCR (Google Vision / Yandex Vision / AWS Textract) — для сложных почерков;
 * - кастомная ML-модель, обученная на конкретных бланках предприятия.
 * <p>
 * Реализация не входит в этот проект и должна быть подключена интеграционно —
 * см. {@link StubOcrEngine} для локальной разработки и тестов.
 */
public interface OcrEngine {

    /**
     * Распознаёт изображение чек-листа и возвращает структурированный результат.
     *
     * @param imageBytes байты изображения (jpg/png/pdf-страница)
     * @return структурированный результат распознавания
     */
    OcrDocumentResult recognize(byte[] imageBytes);
}
