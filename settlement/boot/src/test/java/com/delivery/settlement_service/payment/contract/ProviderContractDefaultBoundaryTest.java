package com.delivery.settlement_service.payment.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderContractDefaultBoundaryTest {

    @Test
    void paymentAndPayoutExecutionRemainDefaultOffInTheMainProfile() throws Exception {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(properties)
                .contains("app.payment.processing-enabled=${PAYMENT_PROCESSING_ENABLED:false}")
                .contains("app.refund.provider-processing-enabled=${REFUND_PROVIDER_PROCESSING_ENABLED:false}")
                .contains("app.payout.processing-enabled=${PAYOUT_PROCESSING_ENABLED:false}")
                .contains("app.payout.provider=${PAYOUT_PROVIDER:PAYOS}");
    }
}
