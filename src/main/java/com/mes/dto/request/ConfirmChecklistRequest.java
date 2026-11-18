package com.mes.dto.request;

import java.util.List;

/**
 * Финальное подтверждение чек-листа оператором. corrections содержит только те строки,
 * которые оператор поправил вручную (OCR ошибся или не нашёл значение) — остальные
 * принимаются как распознанные. controlValues — замеры в контрольных точках рецептуры
 * (pH, плотность и т.п.), которые пока вводятся вручную.
 */
public record ConfirmChecklistRequest(
        String operator,
        List<ConfirmChecklistItemRequest> corrections,
        List<ControlValueRequest> controlValues
) {
    public ConfirmChecklistRequest(String operator, List<ConfirmChecklistItemRequest> corrections) {
        this(operator, corrections, null);
    }
}
