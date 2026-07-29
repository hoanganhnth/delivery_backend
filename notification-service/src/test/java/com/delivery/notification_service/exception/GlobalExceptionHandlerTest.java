package com.delivery.notification_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void conflictingDeduplicationPayloadReturnsCanonicalConflict() {
        var response = new GlobalExceptionHandler().handleConflict(
                new NotificationConflictException("Deduplication key conflict"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isZero();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Deduplication key conflict");
    }
}
