package com.mes.qr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService(new ObjectMapper());

    @Test
    void generateThenDecode_roundTripsPayloadExactly() {
        OrderQrPayload payload = new OrderQrPayload(UUID.randomUUID(), "ORD-2026-042", "SM-17", 3);

        BufferedImage qrImage = qrCodeService.generate(payload);
        Optional<OrderQrPayload> decoded = qrCodeService.decode(qrImage, OrderQrPayload.class);

        assertTrue(decoded.isPresent());
        assertEquals(payload, decoded.get());
    }

    @Test
    void decode_onPlainImageWithoutQr_returnsEmpty() {
        BufferedImage blank = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Optional<OrderQrPayload> decoded = qrCodeService.decode(blank, OrderQrPayload.class);
        assertTrue(decoded.isEmpty());
    }

    @Test
    void generatePng_producesValidPngBytes() {
        OrderQrPayload payload = new OrderQrPayload(UUID.randomUUID(), "ORD-1", "R-1", 1);
        byte[] png = qrCodeService.generatePng(payload);

        assertTrue(png.length > 0);
        // Сигнатура PNG
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
    }
}
