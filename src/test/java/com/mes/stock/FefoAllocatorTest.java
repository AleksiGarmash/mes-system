package com.mes.stock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FefoAllocatorTest {

    private final FefoAllocator allocator = new FefoAllocator();
    private final LocalDate today = LocalDate.of(2026, 6, 1);

    private StockLot lot(String no, String qty, LocalDate expiry) {
        return new StockLot(UUID.randomUUID(), no, new BigDecimal(qty), expiry);
    }

    private BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void takesLotWithEarliestExpiryFirst() {
        StockLot late = lot("L-LATE", "100", today.plusDays(90));
        StockLot soon = lot("L-SOON", "100", today.plusDays(10));

        FefoAllocation a = allocator.allocate(List.of(late, soon), bd("60"), today);

        assertEquals(1, a.picks().size());
        assertEquals("L-SOON", a.picks().get(0).lot().lotNo());
        assertEquals(0, bd("60").compareTo(a.picks().get(0).quantity()));
        assertTrue(a.isComplete());
    }

    @Test
    void spansSeveralLotsInExpiryOrderWhenFirstIsNotEnough() {
        StockLot a1 = lot("A", "40", today.plusDays(5));
        StockLot a2 = lot("B", "40", today.plusDays(20));
        StockLot a3 = lot("C", "40", today.plusDays(60));

        FefoAllocation result = allocator.allocate(List.of(a3, a1, a2), bd("100"), today);

        assertEquals(List.of("A", "B", "C"), result.picks().stream().map(p -> p.lot().lotNo()).toList());
        assertEquals(0, bd("40").compareTo(result.picks().get(0).quantity()));
        assertEquals(0, bd("40").compareTo(result.picks().get(1).quantity()));
        assertEquals(0, bd("20").compareTo(result.picks().get(2).quantity()));
        assertTrue(result.isComplete());
    }

    @Test
    void skipsExpiredAndEmptyLots() {
        StockLot expired = lot("EXPIRED", "500", today.minusDays(1));
        StockLot empty = lot("EMPTY", "0", today.plusDays(3));
        StockLot good = lot("GOOD", "50", today.plusDays(30));

        FefoAllocation result = allocator.allocate(List.of(expired, empty, good), bd("30"), today);

        assertEquals(1, result.picks().size());
        assertEquals("GOOD", result.picks().get(0).lot().lotNo());
    }

    @Test
    void lotExpiringTodayIsStillUsable() {
        FefoAllocation result = allocator.allocate(List.of(lot("TODAY", "10", today)), bd("5"), today);

        assertEquals(1, result.picks().size());
    }

    @Test
    void lotsWithoutExpiryAreUsedLast() {
        StockLot noExpiry = lot("NO-EXP", "100", null);
        StockLot dated = lot("DATED", "100", today.plusDays(200));

        FefoAllocation result = allocator.allocate(List.of(noExpiry, dated), bd("150"), today);

        assertEquals(List.of("DATED", "NO-EXP"), result.picks().stream().map(p -> p.lot().lotNo()).toList());
    }

    @Test
    void equalExpiry_isBrokenDeterministicallyByLotNumber() {
        StockLot b = lot("B-2", "10", today.plusDays(10));
        StockLot a = lot("A-1", "10", today.plusDays(10));

        FefoAllocation result = allocator.allocate(List.of(b, a), bd("5"), today);

        assertEquals("A-1", result.picks().get(0).lot().lotNo());
    }

    @Test
    void insufficientStock_reportsShortageAndTakesEverythingAvailable() {
        FefoAllocation result = allocator.allocate(
                List.of(lot("A", "30", today.plusDays(5)), lot("B", "20", today.plusDays(9))), bd("80"), today);

        assertFalse(result.isComplete());
        assertEquals(0, bd("30").compareTo(result.shortage()));
        BigDecimal taken = result.picks().stream().map(FefoAllocation.Pick::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, bd("50").compareTo(taken));
    }

    @Test
    void noUsableLots_meansFullShortage() {
        FefoAllocation result = allocator.allocate(List.of(lot("OLD", "10", today.minusDays(5))), bd("7"), today);

        assertTrue(result.picks().isEmpty());
        assertEquals(0, bd("7").compareTo(result.shortage()));
    }

    @Test
    void exactFit_leavesNoShortageAndNoExtraPicks() {
        FefoAllocation result = allocator.allocate(
                List.of(lot("A", "50", today.plusDays(5)), lot("B", "50", today.plusDays(9))), bd("50"), today);

        assertEquals(1, result.picks().size());
        assertTrue(result.isComplete());
    }

    @Test
    void nonPositiveRequirement_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(List.of(), BigDecimal.ZERO, today));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(List.of(), bd("-1"), today));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(List.of(), null, today));
    }

    @Test
    void doesNotMutateInputList() {
        List<StockLot> lots = List.of(lot("B", "10", today.plusDays(9)), lot("A", "10", today.plusDays(1)));

        allocator.allocate(lots, bd("5"), today);

        assertEquals("B", lots.get(0).lotNo());
    }
}
