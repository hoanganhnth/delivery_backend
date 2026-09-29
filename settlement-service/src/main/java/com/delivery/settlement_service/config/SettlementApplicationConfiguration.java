package com.delivery.settlement_service.config;

import com.delivery.settlement.application.DefaultPaymentService;
import com.delivery.settlement.application.DefaultPayoutService;
import com.delivery.settlement.application.api.PaymentProviderPort;
import com.delivery.settlement.application.api.PaymentUseCase;
import com.delivery.settlement.application.api.PayoutProviderPort;
import com.delivery.settlement.application.api.PayoutUseCase;
import com.delivery.settlement.domain.payment.PaymentOperationRequest;
import com.delivery.settlement.domain.payment.PayoutRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;
import com.delivery.settlement_service.payment.contract.PayOsClient;
import com.delivery.settlement_service.payment.contract.PayOsProviderAdapter;
import com.delivery.settlement_service.payment.LegacyPaymentWorkflowAdapter;
import com.delivery.settlement_service.payment.PaymentProviderRegistry;
import com.delivery.settlement_service.service.PaymentService;
import com.delivery.settlement.application.api.PaymentWorkflowPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

/** Composes the framework-free settlement application ports for the host. */
@Configuration
@ConditionalOnProperty(name = "app.settlement.application-api-enabled", havingValue = "true", matchIfMissing = true)
public class SettlementApplicationConfiguration {

    @Bean
    PayOsClient settlementPayOsClient() {
        // Provider HTTP integration is deliberately disabled in this host slice.
        // The application boundary still has a deterministic, safe adapter until
        // a credentialed provider client is enabled explicitly.
        return new PayOsClient() {
            @Override public ProviderOperationResult createPayment(PaymentOperationRequest request) {
                return ProviderOperationResult.unknown("payment provider client is not configured");
            }
            @Override public ProviderOperationResult refund(PaymentOperationRequest request) {
                return ProviderOperationResult.unknown("payment provider client is not configured");
            }
            @Override public ProviderOperationResult status(PaymentOperationRequest request) {
                return ProviderOperationResult.unknown("payment provider client is not configured");
            }
            @Override public ProviderOperationResult submitPayout(PayoutRequest request) {
                return ProviderOperationResult.unknown("payout provider client is not configured");
            }
            @Override public ProviderOperationResult payoutStatus(PayoutRequest request) {
                return ProviderOperationResult.unknown("payout provider client is not configured");
            }
        };
    }

    @Bean
    PaymentProviderPort settlementPaymentProvider(PayOsClient client) {
        return new PayOsProviderAdapter(client);
    }

    @Bean
    PayoutProviderPort settlementPayoutProvider(PayOsClient client) {
        return new PayOsProviderAdapter(client);
    }

    @Bean
    PaymentUseCase settlementPaymentUseCase(
            @Qualifier("settlementPaymentProvider") PaymentProviderPort provider) {
        return new DefaultPaymentService(provider);
    }

    @Bean
    @ConditionalOnProperty(name = "app.payment.processing-enabled", havingValue = "true")
    PaymentWorkflowPort settlementPaymentWorkflow(PaymentService paymentService,
            PaymentProviderRegistry providerRegistry) {
        return new LegacyPaymentWorkflowAdapter(paymentService, providerRegistry);
    }

    @Bean
    PayoutUseCase settlementPayoutUseCase(
            @Qualifier("settlementPayoutProvider") PayoutProviderPort provider) {
        return new DefaultPayoutService(provider);
    }
}
