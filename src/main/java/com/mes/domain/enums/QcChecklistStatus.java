package com.mes.domain.enums;

/**
 * Жизненный цикл протокола контроля качества (QC-чек-листа).
 * Аналогичен {@link ChecklistStatus}, но конечное состояние — {@code CONFIRMED}
 * (партия сразу получает RELEASED/REJECTED по результатам — отдельного "производство
 * завершено" здесь нет, партия к этому моменту уже физически произведена).
 */
public enum QcChecklistStatus {
    NEW,
    UPLOADED,
    OCR_COMPLETE,
    WAITING_CONFIRMATION,
    CONFIRMED,
    REJECTED
}
