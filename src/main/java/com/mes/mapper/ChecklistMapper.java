package com.mes.mapper;

import com.mes.domain.ChecklistItem;
import com.mes.domain.ProcessControlRecord;
import com.mes.domain.ProductionChecklist;
import com.mes.dto.response.ChecklistResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChecklistMapper {

    @Mapping(target = "productionOrderId", source = "productionOrder.id")
    ChecklistResponse toResponse(ProductionChecklist checklist);

    ChecklistResponse.ChecklistItemResponse toItemResponse(ChecklistItem item);

    ChecklistResponse.ControlRecordResponse toControlRecordResponse(ProcessControlRecord record);
}
