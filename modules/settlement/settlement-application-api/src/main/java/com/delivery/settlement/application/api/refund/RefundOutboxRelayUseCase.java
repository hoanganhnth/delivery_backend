package com.delivery.settlement.application.api.refund;
public interface RefundOutboxRelayUseCase {
    int SCAN_LIMIT=100;
    void relay();
}
