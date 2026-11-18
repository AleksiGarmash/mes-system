package com.mes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Событие, записанное в той же транзакции, что и бизнес-данные (Transactional Outbox).
 * {@code OutboxRelay} отправляет неотправленные события в Kafka и проставляет {@code sentAt}.
 * Доставка «как минимум один раз»: потребитель должен быть идемпотентным по {@code id} события.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "event_key", length = 100)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
