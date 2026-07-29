package com.delivery.promotion_service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusErrorsUseCanonicalFailureEnvelope() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(0, response.getBody().getStatus());
        assertEquals("Forbidden", response.getBody().getMessage());
        assertNull(response.getBody().getData());
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() {
        var response = handler.handleUnexpected(new RuntimeException("database password leaked"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(0, response.getBody().getStatus());
        assertEquals("Đã xảy ra lỗi nội bộ. Vui lòng thử lại sau.", response.getBody().getMessage());
        assertNull(response.getBody().getData());
    }
}
