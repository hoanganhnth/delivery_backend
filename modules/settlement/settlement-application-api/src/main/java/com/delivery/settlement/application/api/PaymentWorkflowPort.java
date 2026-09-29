package com.delivery.settlement.application.api;

import java.util.Map;
import java.util.Set;

/** Host-facing payment workflow boundary; transport and persistence stay outside the API module. */
public interface PaymentWorkflowPort {
    PaymentWorkflowResult create(PaymentWorkflowCommand command);
    PaymentWorkflowResult callback(String provider, Map<String, String> parameters);
    PaymentWorkflowResult byId(Long paymentId);
    PaymentWorkflowResult byReference(String paymentReference);
    Set<String> availableProviders();
}
