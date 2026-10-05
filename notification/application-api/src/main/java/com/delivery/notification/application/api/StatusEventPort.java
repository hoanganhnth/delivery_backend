package com.delivery.notification.application.api;

import com.delivery.notification.domain.EventIdentity.Status;

public interface StatusEventPort {
    void validateSimulationContext();
    void send(Status status);
}
