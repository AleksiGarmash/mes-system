package com.mes.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Безопасные параметры страницы из запроса: размер ограничен, отрицательные значения исправляются. */
public final class Paging {

    public static final int MAX_PAGE_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(int page, int size, Sort.Direction direction, String sortProperty) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(direction, sortProperty));
    }

    /** Новые записи первыми. */
    public static Pageable newestFirst(int page, int size) {
        return of(page, size, Sort.Direction.DESC, "createdAt");
    }
}
