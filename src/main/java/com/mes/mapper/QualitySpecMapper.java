package com.mes.mapper;

import com.mes.domain.QualitySpec;
import com.mes.domain.QualitySpecItem;
import com.mes.dto.response.QualitySpecResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface QualitySpecMapper {

    QualitySpecResponse toResponse(QualitySpec spec);

    QualitySpecResponse.QualitySpecItemResponse toItemResponse(QualitySpecItem item);
}
