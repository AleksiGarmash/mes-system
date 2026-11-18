package com.mes.service;

import com.mes.domain.AuditLog;
import com.mes.repository.AuditLogRepository;
import com.mes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Журнал изменений. Пишет только реальные изменения (значение до отличается от значения после),
 * автор берётся из JWT, а не из тела запроса, чтобы его нельзя было подменить.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_VALUE_LENGTH = 500;

    private final AuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;

    @Transactional
    public void recordChange(String entityType, UUID entityId, String field, Object oldValue, Object newValue) {
        String before = asText(oldValue);
        String after = asText(newValue);
        if (Objects.equals(before, after)) {
            return;
        }
        auditLogRepository.save(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .field(field)
                .oldValue(before)
                .newValue(after)
                .changedBy(currentUser.username())
                .changedAt(Instant.now())
                .build());
    }

    @Transactional
    public void recordStatus(String entityType, UUID entityId, Object from, Object to) {
        recordChange(entityType, entityId, "status", from, to);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> history(String entityType, UUID entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByChangedAtAsc(entityType, entityId);
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : value.toString();
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) : text;
    }
}
