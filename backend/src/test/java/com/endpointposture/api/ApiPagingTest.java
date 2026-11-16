package com.endpointposture.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiPagingTest {
    @Test
    void clampsPageAndPageSize() {
        var first = ApiPaging.of(-1, 0);
        assertEquals(0, first.getPageNumber());
        assertEquals(1, first.getPageSize());

        var capped = ApiPaging.of(3, Integer.MAX_VALUE);
        assertEquals(3, capped.getPageNumber());
        assertEquals(ApiPaging.MAX_SIZE, capped.getPageSize());
    }
}
