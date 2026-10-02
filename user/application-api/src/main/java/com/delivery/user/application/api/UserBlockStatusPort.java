package com.delivery.user.application.api;

/** Persistence boundary for block-status projection; implemented by infrastructure. */
public interface UserBlockStatusPort {

    UserBlockStatusResult mutate(Long userId,
            java.util.function.UnaryOperator<UserBlockStatusResult> transition);
}
