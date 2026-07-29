package com.delivery.settlement_service.payload;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BaseResponseContractTest {

    @Test
    void namedFactoriesCannotSwapMessageAndData() {
        BaseResponse<BigDecimal> success =
                BaseResponse.success(new BigDecimal("120000"), "Ledger total");
        BaseResponse<Object> failure = BaseResponse.failure("Invalid ledger request");

        assertThat(success.getStatus()).isEqualTo(1);
        assertThat(success.getMessage()).isEqualTo("Ledger total");
        assertThat(success.getData()).isEqualByComparingTo("120000");
        assertThat(failure.getStatus()).isZero();
        assertThat(failure.getMessage()).isEqualTo("Invalid ledger request");
        assertThat(failure.getData()).isNull();
    }
}
