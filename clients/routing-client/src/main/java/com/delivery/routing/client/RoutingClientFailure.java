package com.delivery.routing.client;

/** Failure categories exposed by the routing boundary without choosing caller policy. */
public enum RoutingClientFailure {
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    UNAVAILABLE,
    REMOTE_FAILURE
}
