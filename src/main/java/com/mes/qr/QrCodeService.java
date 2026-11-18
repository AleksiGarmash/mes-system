package com.mes.qr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Генерация и считывание QR-кодов, идентифицирующих конкретный документ
 * (задание — {@link OrderQrPayload}, партию для протокола QC — {@link BatchQrPayload},
 * и любые будущие типы) без риска ошибки OCR на самом критичном поле бланка.
 * <p>
 * Сервис не привязан к конкретному типу полезной нагрузки — тип передаётся явно
 * при декодировании ({@code decode(image, OrderQrPayload.class)}), а не определяется
 * из содержимого QR. Это осознанное упрощение: вызывающий код всегда точно знает,
 * какой документ он читает (чек-лист производства ищет {@code OrderQrPayload},
 * протокол QC — {@code BatchQrPayload}), поэтому угадывать тип не требуется.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeService {

    private static final String CHARSET = "UTF-8";
    private static final int QR_SIZE_PX = 300;

    private final ObjectMapper objectMapper;

    /** Генерирует QR-код с произвольной полезной нагрузкой (сериализуется в JSON) в виде изображения. */
    public BufferedImage generate(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter().encode(json, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, hints);
            return toBufferedImage(matrix);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сгенерировать QR-код", e);
        }
    }

    /** Генерирует QR-код и сразу возвращает PNG-байты (удобно для встраивания в PDF). */
    public byte[] generatePng(Object payload) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(generate(payload), "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить QR-код в PNG", e);
        }
    }

    /**
     * Пытается найти и декодировать QR-код заданного типа на изображении.
     * Возвращает {@code Optional.empty()}, если QR не найден, не распознан, или его
     * содержимое не соответствует ожидаемому типу — это не ошибка, а нормальный случай
     * (например, бланк без QR, или QR попал не в кадр); вызывающий код предусматривает fallback.
     */
    public <T> Optional<T> decode(BufferedImage image, Class<T> payloadType) {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.CHARACTER_SET, CHARSET);

            Result result = new MultiFormatReader().decode(bitmap, hints);
            T payload = objectMapper.readValue(result.getText(), payloadType);
            return Optional.of(payload);
        } catch (NotFoundException e) {
            log.debug("QR-код на изображении не найден");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("QR-код найден, но не удалось разобрать содержимое как {}: {}",
                    payloadType.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    public <T> Optional<T> decode(byte[] imageBytes, Class<T> payloadType) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return Optional.empty();
            }
            return decode(image, payloadType);
        } catch (IOException e) {
            log.warn("Не удалось прочитать изображение для поиска QR-кода: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private BufferedImage toBufferedImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }
}
