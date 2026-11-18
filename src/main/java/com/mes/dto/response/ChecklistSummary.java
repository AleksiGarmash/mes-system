package com.mes.dto.response;

import com.mes.domain.ChecklistItem;
import com.mes.domain.ProductionChecklist;
import com.mes.domain.ProductionOrder;
import com.mes.domain.enums.ChecklistStatus;

import java.time.Instant;
import java.util.UUID;

/** Строка списка чек-листов: статус и сколько строк ждёт проверки оператором. */
public record ChecklistSummary(
        UUID id,
        UUID productionOrderId,
        String orderNo,
        String recipeCode,
        ChecklistStatus status,
        Instant uploadedAt,
        Integer pageCount,
        int itemCount,
        int needsReviewCount,
        Instant createdAt
) {
    public static ChecklistSummary from(ProductionChecklist c) {
        ProductionOrder order = c.getProductionOrder();
        int needsReview = (int) c.getItems().stream().filter(ChecklistItem::isNeedsReview).count();
        return new ChecklistSummary(c.getId(), order.getId(), order.getOrderNo(), order.getRecipe().getCode(),
                c.getStatus(), c.getUploadedAt(), c.getPageCount(), c.getItems().size(), needsReview, c.getCreatedAt());
    }
}
