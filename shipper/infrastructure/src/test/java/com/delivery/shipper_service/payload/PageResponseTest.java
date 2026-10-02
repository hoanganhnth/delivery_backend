package com.delivery.shipper_service.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void mapsSpringPageToStablePublicContract() {
        var source = new PageImpl<>(List.of("shipper-1"), PageRequest.of(0, 1), 2);

        var response = PageResponse.from(source);

        assertEquals(List.of("shipper-1"), response.items());
        assertEquals(0, response.page());
        assertEquals(1, response.size());
        assertEquals(2, response.totalItems());
        assertEquals(2, response.totalPages());
        assertTrue(response.hasNext());
    }
}
