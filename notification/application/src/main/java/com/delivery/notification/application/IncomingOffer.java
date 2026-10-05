package com.delivery.notification.application;

import com.delivery.notification.application.api.OfferEventPort;
import com.delivery.notification.domain.EventIdentity;
import com.delivery.notification.domain.EventIdentity.Offer;

public final class IncomingOffer {
    private final OfferEventPort port;
    public IncomingOffer(OfferEventPort port) { this.port = port; }

    /** False denotes a valid simulation no-op; caller still owns Kafka ACK. */
    public boolean handle(Offer event) {
        EventIdentity.validateOffer(event);
        port.validateSimulationContext();
        if (port.isSimulation()) return false;
        port.send(event, event.shippers().get(0));
        return true;
    }
}
