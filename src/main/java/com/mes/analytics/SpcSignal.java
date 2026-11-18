package com.mes.analytics;

/**
 * Сигнал о нестабильности процесса на контрольной карте индивидуальных значений.
 *
 * @param rule        код правила (WE1–WE4, правила Western Electric)
 * @param index       индекс точки (с 0), на которой сработало правило
 * @param description пояснение для технолога
 */
public record SpcSignal(String rule, int index, String description) {
}
