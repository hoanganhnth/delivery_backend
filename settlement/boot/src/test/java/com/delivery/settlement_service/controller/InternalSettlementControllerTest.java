package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.service.TransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InternalSettlementControllerTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final InternalSettlementController controller =
            new InternalSettlementController(transactionService, "shared-secret");

    @Test
    void rejectsMissingOrWrongInternalCredential() {
        assertThat(controller.isCodEligible(10L, new BigDecimal("100000"), null).getStatusCode().value())
                .isEqualTo(403);
        assertThat(controller.isCodEligible(10L, new BigDecimal("100000"), null).getBody().getStatus())
                .isZero();
        assertThat(controller.isCodEligible(10L, new BigDecimal("100000"), "wrong").getStatusCode().value())
                .isEqualTo(403);
        verifyNoInteractions(transactionService);
    }

    @Test
    void returnsCanonicalEligibilityForAuthenticatedCaller() {
        when(transactionService.checkCodEligibility(10L, new BigDecimal("100000"))).thenReturn(true);

        var response = controller.isCodEligible(
                10L, new BigDecimal("100000"), "shared-secret");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getStatus()).isEqualTo(1);
        assertThat(response.getBody().getData()).isTrue();
    }

    @Test
    void failsClosedWhenServerSecretIsBlank() {
        var blankSecretController = new InternalSettlementController(transactionService, "");

        assertThat(blankSecretController.isCodEligible(
                10L, new BigDecimal("100000"), "").getStatusCode().value()).isEqualTo(403);
    }
}
