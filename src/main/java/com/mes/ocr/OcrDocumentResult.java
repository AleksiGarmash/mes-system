package com.mes.ocr;

import java.util.List;

/**
 * Результат распознавания одного чек-листа целиком: заголовок (номер задания,
 * рецептура, дата) плюс список распознанных строк компонентов.
 */
public record OcrDocumentResult(
        OcrField<String> orderNumber,
        OcrField<String> recipeCode,
        List<OcrLineItem> lineItems,
        double overallConfidence
) {}
