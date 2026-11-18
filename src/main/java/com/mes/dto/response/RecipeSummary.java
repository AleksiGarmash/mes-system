package com.mes.dto.response;

import com.mes.domain.Recipe;
import com.mes.domain.enums.RecipeStatus;

import java.util.UUID;

public record RecipeSummary(
        UUID id,
        String code,
        String name,
        Integer version,
        RecipeStatus status,
        String qualitySpecCode,
        int stageCount,
        int itemCount,
        int controlPointCount
) {
    public static RecipeSummary from(Recipe r) {
        return new RecipeSummary(r.getId(), r.getCode(), r.getName(), r.getVersion(), r.getStatus(),
                r.getQualitySpecCode(), r.getStages().size(), r.getItems().size(), r.getControlPoints().size());
    }
}
