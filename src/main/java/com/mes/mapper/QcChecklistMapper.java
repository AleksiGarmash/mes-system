package com.mes.mapper;

import com.mes.domain.QcChecklist;
import com.mes.domain.QcChecklistItem;
import com.mes.dto.response.QcChecklistResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QcChecklistMapper {

    @Mapping(target = "batchId", source = "batch.id")
    @Mapping(target = "batchNo", source = "batch.batchNo")
    @Mapping(target = "qualitySpecId", source = "qualitySpec.id")
    QcChecklistResponse toResponse(QcChecklist checklist);

    QcChecklistResponse.QcChecklistItemResponse toItemResponse(QcChecklistItem item);
}
