package com.mes.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateRecipeRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotEmpty @Valid List<RecipeItemRequest> items,
        String qualitySpecCode,
        Boolean requireRawMaterialLot,
        @Valid List<RecipeStageRequest> stages,
        @Valid List<RecipeControlPointRequest> controlPoints
) {
    /** Простая одностадийная рецептура без контрольных точек. */
    public CreateRecipeRequest(String code, String name, List<RecipeItemRequest> items) {
        this(code, name, items, null, null, null, null);
    }
}
