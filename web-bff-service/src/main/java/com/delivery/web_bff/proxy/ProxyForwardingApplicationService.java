package com.delivery.web_bff.proxy;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authenticates and sanitizes proxy traffic before delegating to a gateway adapter. */
public final class ProxyForwardingApplicationService implements UseCases.ProxyForwarding {
    private static final Set<String> REQUEST_HEADERS = Set.of("Accept", "Content-Type", "Idempotency-Key",
            "If-Match", "If-None-Match", "X-Correlation-ID");
    private final UseCases.ApiPolicyEvaluation policy;
    private final UseCases.AccessTokenResolution access;
    private final Ports.ProxyForwarding gateway;

    public ProxyForwardingApplicationService(UseCases.ApiPolicyEvaluation policy,
            UseCases.AccessTokenResolution access, Ports.ProxyForwarding gateway) {
        this.policy = policy; this.access = access; this.gateway = gateway;
    }

    @Override
    public Ports.ForwardedResponse execute(UseCases.ProxyRequest request) {
        if (request == null || !policy.execute(request.method(), request.path()))
            throw new UseCases.ApiProxyRejectedException("API path or method is not allowed");
        boolean mutation = switch (request.method()) {
            case POST, PUT, PATCH, DELETE -> true;
            case GET, HEAD, OPTIONS -> false;
        };
        String bearer = access.execute(request.rawSessionId(), request.csrfToken(), mutation);
        Map<String, List<String>> safe = request.headers() == null ? Map.of() : request.headers().entrySet().stream()
                .filter(e -> REQUEST_HEADERS.contains(e.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        return gateway.forward(request.method(), request.path(), request.query(), safe,
                request.body() == null ? new byte[0] : request.body(), bearer);
    }

}
