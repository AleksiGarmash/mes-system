package com.mes.ocr;

import java.math.BigDecimal;

/**
 * Одна распознанная строка чек-листа: "Компонент / Партия / Факт (кг)".
 * Любое поле может отсутствовать (OCR не нашёл / не смог распознать) —
 * тогда соответствующий OcrField будет null, и строка помечается на ручную проверку.
 */
public record OcrLineItem(
        OcrField<String> materialName,
        OcrField<String> batchNumber,
        OcrField<BigDecimal> weight
) {
    public boolean isComplete() {
        return materialName != null && batchNumber != null && weight != null;
    }
}
