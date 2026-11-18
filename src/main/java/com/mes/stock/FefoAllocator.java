package com.mes.stock;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Подбор лотов по принципу FEFO (First Expired, First Out): первым выдаётся лот с ближайшим сроком
 * годности. Просроченные и пустые лоты пропускаются. Лоты без срока годности идут последними
 * (их нечем «торопить»), при равных сроках порядок детерминирован по номеру лота. Лот, срок
 * которого истекает сегодня, ещё годен. Ничего не списывает: только считает.
 */
@Component
public class FefoAllocator {

    private static final Comparator<StockLot> FEFO_ORDER = Comparator
            .comparing(StockLot::expiry, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockLot::lotNo, Comparator.nullsLast(Comparator.naturalOrder()));

    public FefoAllocation allocate(List<StockLot> lots, BigDecimal required, LocalDate today) {
        if (required == null || required.signum() <= 0) {
            throw new IllegalArgumentException("Потребность должна быть положительной");
        }

        List<StockLot> usable = lots.stream()
                .filter(l -> l.available() != null && l.available().signum() > 0)
                .filter(l -> l.expiry() == null || !l.expiry().isBefore(today))
                .sorted(FEFO_ORDER)
                .toList();

        List<FefoAllocation.Pick> picks = new ArrayList<>();
        BigDecimal remaining = required;
        for (StockLot lot : usable) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = lot.available().min(remaining);
            picks.add(new FefoAllocation.Pick(lot, take));
            remaining = remaining.subtract(take);
        }
        return new FefoAllocation(picks, remaining);
    }
}
