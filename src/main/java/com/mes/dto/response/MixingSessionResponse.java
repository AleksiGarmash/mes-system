package com.mes.dto.response;

import com.mes.domain.enums.MixingSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record MixingSessionResponse(
        UUID id,
        UUID batchId,
        UUID mixerId,
        MixingSessionStatus status,
        Instant startedAt,
        Instant finishedAt
) {}
