package com.delivery.analytics_service.service;
import com.delivery.analytics.domain.ReceiptIdentity;
import com.delivery.analytics_service.entity.AnalyticsEvent;
/** JPA mapping only; receipt replay decisions belong to the domain. */
final class AnalyticsReplayPolicy {
    private AnalyticsReplayPolicy() {}
    static ReceiptIdentity identity(AnalyticsEvent event) {
        return new ReceiptIdentity(event.getEventType(),event.getOrderId(),event.getUserId(),
                event.getRestaurantId(),event.getRestaurantName(),event.getAmount(),event.getOrderStatus(),
                event.getPaymentMethod(),event.getAggregateVersion(),event.getRawPayload(),event.getPayloadFingerprint());
    }
    static void requireExactReplay(AnalyticsEvent existing, AnalyticsEvent incoming) {
        identity(existing).requireExactReplay(identity(incoming));
    }
}
