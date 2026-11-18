package com.mes.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateQualitySpecRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotEmpty @Valid List<QualitySpecItemRequest> items
) {}
