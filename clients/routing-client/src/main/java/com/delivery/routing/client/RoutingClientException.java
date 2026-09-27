package com.delivery.routing.client;

import org.springframework.http.HttpStatusCode;

/** Exception raised when the routing boundary cannot return a typed response. */
public final class RoutingClientException extends RuntimeException {

    private final RoutingClientFailure kind;
    private final HttpStatusCode statusCode;

    public RoutingClientException(
            RoutingClientFailure kind, String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public RoutingClientFailure kind() {
        return kind;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }
}
