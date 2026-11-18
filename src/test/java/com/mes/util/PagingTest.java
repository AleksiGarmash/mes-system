package com.mes.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingTest {

    @Test
    void sizeIsCappedAtMaximum() {
        Pageable p = Paging.of(0, 10_000, Sort.Direction.DESC, "createdAt");
        assertEquals(Paging.MAX_PAGE_SIZE, p.getPageSize());
    }

    @Test
    void negativeOrZeroValuesAreCorrected() {
        Pageable p = Paging.of(-3, 0, Sort.Direction.ASC, "expiry");
        assertEquals(0, p.getPageNumber());
        assertEquals(1, p.getPageSize());
    }

    @Test
    void normalValuesArePassedThrough() {
        Pageable p = Paging.newestFirst(2, 25);
        assertEquals(2, p.getPageNumber());
        assertEquals(25, p.getPageSize());
    }
}
