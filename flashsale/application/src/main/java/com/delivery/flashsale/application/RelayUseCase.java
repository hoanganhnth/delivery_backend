package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.RelayPort;
import static com.delivery.flashsale.domain.FlashSaleEventPolicy.*;

public final class RelayUseCase<E> {
    private final RelayPort<E> port;
    public RelayUseCase(RelayPort<E> port) { this.port = port; }
    public void relay() {
        for (E event : port.due(port.now(), 100)) {
            try { port.publish(event); port.sent(event, port.now()); }
            catch (Exception exception) {
                int attempts = port.attempts(event) + 1;
                port.failed(event, attempts, lastError(exception.getMessage()));
                if (dead(attempts)) port.dead(event, exception);
                else port.retryAt(event, port.now().plusSeconds(retrySeconds(attempts)));
            }
        }
    }
}
