package com.mes.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FileHashingTest {

    @Test
    void sha256_matchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                FileHashing.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sha256_differsForDifferentContent() {
        assertNotEquals(FileHashing.sha256Hex(new byte[]{1}), FileHashing.sha256Hex(new byte[]{2}));
    }
}
