package com.delivery.user.application.api;

/** Application boundary for Auth-to-User block-status projection. */
public interface UserBlockStatusUseCase {

    UserBlockStatusResult update(UpdateUserBlockStatusCommand command);
}
