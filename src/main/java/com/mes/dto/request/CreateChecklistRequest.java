package com.mes.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateChecklistRequest(
        @NotNull UUID productionOrderId
) {}
