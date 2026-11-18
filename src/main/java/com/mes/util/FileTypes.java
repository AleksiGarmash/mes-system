package com.mes.util;

import java.util.Locale;

/** Соответствие типа содержимого и расширения файла в хранилище. */
public final class FileTypes {

    private FileTypes() {
    }

    /** Расширение по типу содержимого: PDF не должен храниться под именем .jpg. Неизвестный тип даёт {@code bin}. */
    public static String extensionFor(String contentType) {
        if (contentType == null) {
            return "bin";
        }
        return switch (contentType.toLowerCase(Locale.ROOT).trim()) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            default -> "bin";
        };
    }
}
