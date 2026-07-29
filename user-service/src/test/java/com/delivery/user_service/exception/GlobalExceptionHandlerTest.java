package com.delivery.user_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusUsesTheCanonicalEnvelope() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isZero();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("User not found");
    }

    @Test
    void unexpectedFailureDoesNotExposeItsMessage() {
        var response = handler.handleAll(new IllegalStateException("database password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Đã xảy ra lỗi nội bộ.");
    }
}
