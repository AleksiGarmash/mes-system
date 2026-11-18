package com.mes.mapper;

import com.mes.domain.Recipe;
import com.mes.domain.RecipeControlPoint;
import com.mes.domain.RecipeItem;
import com.mes.domain.RecipeStage;
import com.mes.dto.response.RecipeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RecipeMapper {

    RecipeResponse toResponse(Recipe recipe);

    RecipeResponse.RecipeStageResponse toStageResponse(RecipeStage stage);

    @Mapping(target = "stageNo", source = "stage.stageNo")
    RecipeResponse.RecipeItemResponse toItemResponse(RecipeItem item);

    RecipeResponse.RecipeControlPointResponse toControlPointResponse(RecipeControlPoint controlPoint);
}
