package com.endpointposture.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Shared bounds for list endpoints that accept page and size parameters. */
public final class ApiPaging {
    public static final int DEFAULT_SIZE = 100;
    public static final int MAX_SIZE = 500;

    private ApiPaging() {}

    public static Pageable of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_SIZE)), sort);
    }
}
