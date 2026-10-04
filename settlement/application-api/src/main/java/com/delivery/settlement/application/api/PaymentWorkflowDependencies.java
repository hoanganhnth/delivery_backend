package com.delivery.settlement.application.api;

import com.delivery.settlement.domain.payment.WorkflowPayment;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Technical operations; orchestration and payment decisions belong to the core. */
public interface PaymentWorkflowDependencies {
    interface Store {
        Optional<WorkflowPayment> byId(Long id);
        Optional<WorkflowPayment> byReference(String reference);
        /** Loads the payment holding its row lock until the enclosing transaction ends. */
        Optional<WorkflowPayment> byReferenceForUpdate(String reference);
        void save(WorkflowPayment payment);
    }
    interface Providers {
        Provider get(String name);
        Set<String> available();
    }
    interface Provider {
        Creation create(Request request);
        Verification verify(Map<String, String> parameters);
    }
    interface Effects {
        // Observation only: implementations must not influence workflow decisions.
        default void creating(PaymentWorkflowCommand command, String provider) {}
        default void created(WorkflowPayment payment) {}
        default void callbackStarted(String provider) {}
        default void signatureRejected(String provider, String message) {}
        default void replayed(WorkflowPayment payment) {}
        default void amountMismatch(WorkflowPayment payment, Long received) {}
        default void confirmingFake(String reference) {}
        Long topUp(WorkflowPayment payment);
        void success(WorkflowPayment payment);
        void failed(WorkflowPayment payment, String reason);
    }
    record Request(String reference, BigDecimal amount, String currency, String orderInfo,
            String returnUrl, String ipAddress, String locale) {}
    record Creation(boolean success, String paymentUrl, String transactionId, String error) {}
    record Verification(boolean verified, boolean success, String reference, String transactionId,
            String payload, Long amount, String message) {}
}
