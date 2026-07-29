package com.delivery.settlement_service.exception;

import com.delivery.settlement_service.payload.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void expectedErrorsPutDetailInMessageInsteadOfData() {
        ResponseEntity<BaseResponse<Object>> response =
                handler.handleNotFound(new ResourceNotFoundException("transaction missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("transaction missing");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void unexpectedErrorsDoNotExposeInternalExceptionDetails() {
        ResponseEntity<BaseResponse<Object>> response =
                handler.handleAll(new IllegalStateException("database password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
        assertThat(response.getBody().getData()).isNull();
    }
}
