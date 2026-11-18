package com.mes.event;

import com.mes.domain.OutboxEvent;
import com.mes.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class OutboxRelayTest {

    private OutboxRepository repository;
    private KafkaTemplate<String, String> kafka;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(OutboxRepository.class);
        kafka = Mockito.mock(KafkaTemplate.class);
        relay = new OutboxRelay(repository, kafka);
    }

    private OutboxEvent event(String payload) {
        return OutboxEvent.builder().id(UUID.randomUUID()).topic("t").eventKey("k")
                .eventType("X").payload(payload).createdAt(Instant.now()).build();
    }

    @Test
    void relay_sendsPendingEventsAndMarksThemSent() {
        OutboxEvent a = event("a");
        OutboxEvent b = event("b");
        when(repository.lockNextBatch()).thenReturn(List.of(a, b));
        when(kafka.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        int sent = relay.relay();

        assertEquals(2, sent);
        assertNotNull(a.getSentAt());
        assertNotNull(b.getSentAt());
        verify(kafka).send(eq("t"), eq("k"), eq("a"));
        verify(kafka).send(eq("t"), eq("k"), eq("b"));
    }

    @Test
    void relay_onFailure_keepsEventUnsentRecordsErrorAndStopsToPreserveOrder() {
        OutboxEvent a = event("a");
        OutboxEvent b = event("b");
        when(repository.lockNextBatch()).thenReturn(List.of(a, b));
        when(kafka.send(eq("t"), eq("k"), eq("a")))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        int sent = relay.relay();

        assertEquals(0, sent);
        assertNull(a.getSentAt());
        assertEquals(1, a.getAttempts());
        assertTrue(a.getLastError().contains("broker down"));
        assertNull(b.getSentAt());
        verify(kafka, never()).send(eq("t"), eq("k"), eq("b"));
    }

    @Test
    void relay_nothingPending_doesNothing() {
        when(repository.lockNextBatch()).thenReturn(List.of());

        assertEquals(0, relay.relay());
        verify(kafka, never()).send(any(), any(), any());
    }

    @Test
    void relay_successfulRetryClearsPreviousError() {
        OutboxEvent a = event("a");
        a.setAttempts(3);
        a.setLastError("old error");
        when(repository.lockNextBatch()).thenReturn(List.of(a));
        when(kafka.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        assertNotNull(a.getSentAt());
        assertNull(a.getLastError());
    }
}
