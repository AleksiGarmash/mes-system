package com.mes.service;

import com.mes.util.FileTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

/**
 * Хранение фото/сканов заполненных чек-листов в MinIO/S3.
 * Это архив первичного документа для аудита — оригинал никогда не удаляется.
 */
@Service
@RequiredArgsConstructor
public class ChecklistStorageService {

    private final S3Client s3Client;

    @Value("${mes.storage.s3.bucket}")
    private String bucket;

    public String store(UUID checklistId, byte[] imageBytes, String contentType) {
        String key = "checklists/%s/%s.%s".formatted(checklistId, Instant.now().toEpochMilli(), FileTypes.extensionFor(contentType));

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(imageBytes)
        );

        return key;
    }

    public byte[] retrieve(String key) {
        try (InputStream in = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать файл из хранилища: " + key, e);
        }
    }
}
