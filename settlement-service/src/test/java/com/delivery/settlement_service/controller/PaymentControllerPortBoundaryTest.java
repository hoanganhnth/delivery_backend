package com.delivery.settlement_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.settlement.application.api.PaymentWorkflowPort;
import com.delivery.settlement.application.api.PaymentWorkflowResult;
import com.delivery.settlement_service.dto.request.CreatePaymentRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PaymentControllerPortBoundaryTest {

    @Test
    void createPreservesPublicPaymentUrlResponseThroughWorkflowPort() {
        PaymentWorkflowPort workflow = mock(PaymentWorkflowPort.class);
        PaymentWorkflowResult result = new PaymentWorkflowResult(9L, "PAY-9", 7L, "SHIPPER", "VNPAY",
                BigDecimal.TEN, "VND", "DEPOSIT_TOPUP", "PENDING", "https://pay.example/9", "provider-9",
                null, LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T00:15:00"));
        when(workflow.create(org.mockito.ArgumentMatchers.any())).thenReturn(result);
        PaymentController controller = new PaymentController(workflow);
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setEntityId(7L);
        request.setEntityType("SHIPPER");
        request.setAmount(BigDecimal.TEN);
        request.setPurpose("DEPOSIT_TOPUP");

        var response = controller.createPayment(request, new MockHttpServletRequest());

        verify(workflow).create(org.mockito.ArgumentMatchers.argThat(command ->
                command.entityId().equals(7L) && command.ipAddress().equals("127.0.0.1")));
        assertThat(response.getBody().getData().getPaymentUrl()).isEqualTo("https://pay.example/9");
        assertThat(response.getBody().getData().getPaymentRef()).isEqualTo("PAY-9");
    }
}
