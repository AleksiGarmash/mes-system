package com.mes.service;

import com.mes.domain.AuditLog;
import com.mes.repository.AuditLogRepository;
import com.mes.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private AuditLogRepository repository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AuditLogRepository.class);
        CurrentUser currentUser = Mockito.mock(CurrentUser.class);
        when(currentUser.username()).thenReturn("operator1");
        auditService = new AuditService(repository, currentUser);
    }

    @Test
    void recordChange_differentValues_isSavedWithAuthorFromCurrentUser() {
        UUID id = UUID.randomUUID();

        auditService.recordChange("ITEM", id, "weight", new BigDecimal("749.4"), new BigDecimal("750"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals("ITEM", saved.getEntityType());
        assertEquals(id, saved.getEntityId());
        assertEquals("749.4", saved.getOldValue());
        assertEquals("750", saved.getNewValue());
        assertEquals("operator1", saved.getChangedBy());
    }

    @Test
    void recordChange_sameValueWithDifferentScale_isNotLogged() {
        auditService.recordChange("ITEM", UUID.randomUUID(), "weight", new BigDecimal("750.00"), new BigDecimal("750"));

        verify(repository, never()).save(any());
    }

    @Test
    void recordChange_nullToValue_isLoggedWithNullOldValue() {
        auditService.recordChange("ITEM", UUID.randomUUID(), "note", null, "вводить медленно");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getOldValue());
        assertEquals("вводить медленно", captor.getValue().getNewValue());
    }

    @Test
    void recordChange_bothNull_isNotLogged() {
        auditService.recordChange("ITEM", UUID.randomUUID(), "note", null, null);

        verify(repository, never()).save(any());
    }

    @Test
    void recordStatus_logsStatusField() {
        auditService.recordStatus("CHECKLIST", UUID.randomUUID(), "UPLOADED", "WAITING_CONFIRMATION");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertEquals("status", captor.getValue().getField());
    }

    @Test
    void recordChange_veryLongValue_isTruncatedTo500() {
        auditService.recordChange("ITEM", UUID.randomUUID(), "note", null, "x".repeat(900));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertEquals(500, captor.getValue().getNewValue().length());
    }
}
