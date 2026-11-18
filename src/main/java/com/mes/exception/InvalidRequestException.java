package com.mes.exception;

/** Запрос синтаксически корректен, но нарушает правила предметной области (например, несуществующая стадия). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
