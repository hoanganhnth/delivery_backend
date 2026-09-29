package com.delivery.user.application.api;

/** Persistence boundary for block-status projection; implemented by infrastructure. */
public interface UserBlockStatusPort {

    UserBlockStatusResult update(UpdateUserBlockStatusCommand command);
}
