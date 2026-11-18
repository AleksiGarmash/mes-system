package com.mes.dto.response;

/** Оригинал загруженного документа (первичный документ для аудита). */
public record StoredFile(byte[] content, String contentType, String filename) {
}
