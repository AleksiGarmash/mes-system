package com.mes.event;

import com.mes.domain.OutboxEvent;
import com.mes.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Отправляет неотправленные события из outbox в Kafka. Порядок сохраняется: при первой ошибке
 * пакет прерывается, чтобы более новые события не обогнали более старые. Выборка с блокировкой
 * строк ({@code FOR UPDATE SKIP LOCKED}) позволяет запускать несколько экземпляров приложения
 * без двойной отправки. Гарантия доставки — «как минимум один раз»: потребитель идемпотентен по
 * {@code eventId} из конверта.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final long SEND_TIMEOUT_SECONDS = 5;
    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** @return число успешно отправленных событий */
    @Scheduled(fixedDelayString = "${mes.outbox.poll-ms:2000}")
    @Transactional
    public int relay() {
        List<OutboxEvent> pending = outboxRepository.lockNextBatch();
        int sent = 0;
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.setSentAt(Instant.now());
                event.setLastError(null);
                outboxRepository.save(event);
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markFailed(event, e);
                break;
            } catch (Exception e) {
                markFailed(event, e);
                break;
            }
        }
        return sent;
    }

    private void markFailed(OutboxEvent event, Exception e) {
        String message = String.valueOf(e.getMessage());
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
        outboxRepository.save(event);
        log.warn("Не удалось отправить событие {} ({}): {}", event.getId(), event.getEventType(), message);
    }
}
