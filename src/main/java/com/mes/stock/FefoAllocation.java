package com.mes.stock;

import java.math.BigDecimal;
import java.util.List;

/**
 * Результат подбора лотов под потребность.
 *
 * @param picks    выбранные лоты и количества, в порядке выдачи
 * @param shortage сколько не хватило (0, если потребность закрыта полностью)
 */
public record FefoAllocation(List<Pick> picks, BigDecimal shortage) {

    public record Pick(StockLot lot, BigDecimal quantity) {
    }

    public boolean isComplete() {
        return shortage.signum() == 0;
    }
}
