package com.delivery.auth_service.payload;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaseResponseContractTest {

    @Test
    void namedFactoriesCannotSwapMessageAndData() {
        BaseResponse<Map<String, String>> success =
                BaseResponse.success(Map.of("token", "opaque"), "Authenticated");
        BaseResponse<Object> failure = BaseResponse.failure("Invalid credentials");

        assertThat(success.getStatus()).isEqualTo(1);
        assertThat(success.getMessage()).isEqualTo("Authenticated");
        assertThat(success.getData()).containsEntry("token", "opaque");
        assertThat(failure.getStatus()).isZero();
        assertThat(failure.getMessage()).isEqualTo("Invalid credentials");
        assertThat(failure.getData()).isNull();
    }
}
