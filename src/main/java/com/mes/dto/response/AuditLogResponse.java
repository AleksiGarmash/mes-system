package com.mes.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String field,
        String oldValue,
        String newValue,
        String changedBy,
        Instant changedAt
) {}
