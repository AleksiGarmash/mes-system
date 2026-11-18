package com.mes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.mes.domain.OutboxEvent;
import com.mes.event.MesEvent;
import com.mes.event.MesTopics;
import com.mes.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTest {

    private OutboxRepository repository;
    private OutboxService outboxService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new SimpleModule().addSerializer(Instant.class, ToStringSerializer.instance));

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(OutboxRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        outboxService = new OutboxService(repository, mapper);
    }

    @Test
    void enqueue_buildsEnvelopeWithIdempotencyKeyAndSchemaVersion() throws Exception {
        UUID batchId = UUID.randomUUID();
        MesEvent event = new MesEvent.BatchStarted(batchId, "B-2026-000001", UUID.randomUUID(), Instant.parse("2026-01-01T10:00:00Z"));

        OutboxEvent saved = outboxService.enqueue(MesTopics.BATCH_EVENTS, batchId.toString(), event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertSame(saved, captor.getValue());
        assertEquals(MesTopics.BATCH_EVENTS, saved.getTopic());
        assertEquals(batchId.toString(), saved.getEventKey());
        assertEquals("BatchStarted", saved.getEventType());
        assertNull(saved.getSentAt());

        JsonNode json = mapper.readTree(saved.getPayload());
        assertEquals(saved.getId().toString(), json.get("eventId").asText());
        assertEquals("BatchStarted", json.get("type").asText());
        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("B-2026-000001", json.get("data").get("batchNo").asText());
    }

    @Test
    void enqueue_generatesDistinctEventIds() {
        MesEvent event = new MesEvent.RecipeActivated(UUID.randomUUID(), "R-1", 1, Instant.now());

        OutboxEvent first = outboxService.enqueue(MesTopics.RECIPE_EVENTS, "R-1", event);
        OutboxEvent second = outboxService.enqueue(MesTopics.RECIPE_EVENTS, "R-1", event);

        assertNotEquals(first.getId(), second.getId());
    }
}
