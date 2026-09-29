package com.delivery.web_bff.proxy;

import com.delivery.web_bff.application.api.Ports;
import org.springframework.http.HttpMethod;

/** Central, closed allowlist for browser-to-platform API access. */
final class ApiProxyPolicy {
    private ApiProxyPolicy() { }

    static boolean allows(HttpMethod method, String path) {
        if (method == null) return false;
        try { return new ApiProxyPolicyApplicationService().execute(Ports.HttpVerb.valueOf(method.name()), path); }
        catch (IllegalArgumentException ignored) { return false; }
    }
}
