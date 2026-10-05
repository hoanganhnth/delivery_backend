package com.delivery.notification.application.api;

import com.delivery.notification.domain.EventIdentity.Offer;
import com.delivery.notification.domain.EventIdentity.SelectedShipper;

public interface OfferEventPort {
    void validateSimulationContext();
    boolean isSimulation();
    void send(Offer offer, SelectedShipper selected);
}
