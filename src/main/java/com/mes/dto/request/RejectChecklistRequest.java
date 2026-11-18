package com.mes.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectChecklistRequest(
        @NotBlank String reason
) {}
