package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.RecurringStockPort;

public final class RecurringStockUseCase {
    private final RecurringStockPort port;
    public RecurringStockUseCase(RecurringStockPort port) { this.port = port; }
    public int reset() { return port.resetApprovedRecurringStock(); }
}
