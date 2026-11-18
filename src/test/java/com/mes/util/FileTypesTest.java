package com.mes.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTypesTest {

    @Test
    void knownTypes() {
        assertEquals("pdf", FileTypes.extensionFor("application/pdf"));
        assertEquals("png", FileTypes.extensionFor("image/png"));
        assertEquals("jpg", FileTypes.extensionFor("image/jpeg"));
        assertEquals("jpg", FileTypes.extensionFor("image/jpg"));
    }

    @Test
    void caseAndWhitespaceAreIgnored() {
        assertEquals("pdf", FileTypes.extensionFor(" Application/PDF "));
    }

    @Test
    void unknownOrMissingTypeFallsBackToBin() {
        assertEquals("bin", FileTypes.extensionFor("text/html"));
        assertEquals("bin", FileTypes.extensionFor(null));
    }
}
