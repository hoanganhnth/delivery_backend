package com.delivery.web_bff.proxy;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import java.util.Set;

/** Closed browser API policy; deliberately independent of Spring HTTP types. */
public final class ApiProxyPolicyApplicationService implements UseCases.ApiPolicyEvaluation {
    private static final Set<String> RESOURCES = Set.of("users", "addresses", "restaurants", "menu-items", "orders",
            "deliveries", "shippers", "notifications", "tracking", "livestreams", "settlement", "promotions",
            "analytics", "flashsales", "search");
    private static final Set<String> DENIED = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/social-login",
            "/api/auth/refresh-token", "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/reset-password",
            "/api/auth/email-verification/request", "/api/auth/email-verification/confirm");

    @Override
    public boolean execute(Ports.HttpVerb method, String path) {
        if (method == null || path == null || !path.startsWith("/api/") || path.contains("..")
                || path.contains("//") || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0
                || path.indexOf(';') >= 0) return false;
        String encoded = path.toLowerCase(java.util.Locale.ROOT);
        if (encoded.contains("%2e") || encoded.contains("%2f") || encoded.contains("%5c")) return false;
        if (path.contains("/internal/") || path.endsWith("/internal") || DENIED.contains(path)
                || path.startsWith("/api/auth/registrations/")) return false;
        if (path.startsWith("/api/auth/")) return path.equals("/api/auth/sessions")
                || path.matches("/api/auth/sessions/[^/]+")
                || path.equals("/api/auth/firebase/chat-token")
                || path.matches("/api/auth/admin/accounts/[0-9]+/(block|unblock)")
                || path.matches("/api/auth/accounts/[0-9]+");
        int slash = path.indexOf('/', 5);
        String resource = slash < 0 ? path.substring(5) : path.substring(5, slash);
        return RESOURCES.contains(resource);
    }
}
