package com.mes.exception;

/**
 * QR-код задания на бланке (если найден) кодирует id задания. Если он не совпадает
 * с заданием, к которому изначально привязан этот ProductionChecklist — это значит,
 * что оператор сфотографировал/загрузил не тот бланк (перепутал листы на смене).
 * Лучше остановить процесс здесь, чем создать партию под неверным заданием.
 */
public class QrVerificationException extends RuntimeException {
    public QrVerificationException(String message) {
        super(message);
    }
}
