package com.mes.ocr;

/**
 * Одно распознанное поле документа: значение + уверенность модели (0.0–1.0).
 */
public record OcrField<T>(T value, double confidence) {

    public boolean isConfident(double threshold) {
        return confidence >= threshold;
    }
}
