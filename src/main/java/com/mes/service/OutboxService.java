package com.mes.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.domain.OutboxEvent;
import com.mes.event.MesEvent;
import com.mes.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Запись событий в outbox. Конверт: {@code eventId} (для идемпотентности потребителя),
 * {@code type}, {@code schemaVersion} (для эволюции схемы), {@code occurredAt} и {@code data}.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent enqueue(String topic, String key, MesEvent event) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String type = event.getClass().getSimpleName();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", id.toString());
        envelope.put("type", type);
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("occurredAt", now.toString());
        envelope.put("data", event);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать событие " + type, e);
        }

        return outboxRepository.save(OutboxEvent.builder()
                .id(id)
                .topic(topic)
                .eventKey(key)
                .eventType(type)
                .payload(payload)
                .createdAt(now)
                .build());
    }
}
