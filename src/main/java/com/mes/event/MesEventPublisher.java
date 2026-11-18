package com.mes.event;

import com.mes.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Публикация событий предметной области. Событие не уходит в Kafka напрямую: оно записывается
 * в таблицу outbox в той же транзакции, что и бизнес-изменение (если транзакция откатится, события
 * не будет; если Kafka недоступна, событие дождётся её и уйдёт позже). Отправку выполняет
 * {@link OutboxRelay}.
 */
@Component
@RequiredArgsConstructor
public class MesEventPublisher {

    private final OutboxService outboxService;

    public void publishBatchEvent(String key, MesEvent event) {
        outboxService.enqueue(MesTopics.BATCH_EVENTS, key, event);
    }

    public void publishRecipeEvent(String key, MesEvent event) {
        outboxService.enqueue(MesTopics.RECIPE_EVENTS, key, event);
    }

    public void publishQualityEvent(String key, MesEvent event) {
        outboxService.enqueue(MesTopics.QUALITY_EVENTS, key, event);
    }
}
