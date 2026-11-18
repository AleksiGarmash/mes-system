package com.mes.mapper;

import com.mes.domain.*;
import com.mes.dto.response.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Преобразование производственных сущностей в DTO. Вызывать внутри транзакции (ленивые связи). */
@Mapper(componentModel = "spring")
public interface ProductionMapper {

    @Mapping(target = "recipeId", source = "recipe.id")
    @Mapping(target = "recipeCode", source = "recipe.code")
    @Mapping(target = "recipeVersion", source = "recipe.version")
    OrderResponse toResponse(ProductionOrder order);

    @Mapping(target = "productionOrderId", source = "productionOrder.id")
    @Mapping(target = "orderNo", source = "productionOrder.orderNo")
    BatchResponse toResponse(Batch batch);

    @Mapping(target = "batchId", source = "batch.id")
    MixingSessionResponse toResponse(MixingSession session);

    @Mapping(target = "mixingSessionId", source = "mixingSession.id")
    @Mapping(target = "recipeItemId", source = "recipeItem.id")
    @Mapping(target = "rawMaterialBatchId", source = "rawMaterialBatch.id")
    WeighingRecordResponse toResponse(WeighingRecord record);

    @Mapping(target = "batchId", source = "batch.id")
    QualityCheckResponse toResponse(QualityCheck check);

    AuditLogResponse toResponse(AuditLog log);
}
