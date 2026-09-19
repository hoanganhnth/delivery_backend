package com.delivery.web_bff.proxy;

import java.util.Set;
import org.springframework.http.HttpMethod;

/** Central, closed allowlist for browser-to-platform API access. */
final class ApiProxyPolicy {
    private static final Set<String> RESOURCES = Set.of(
            "users", "addresses", "restaurants", "menu-items", "orders", "deliveries",
            "shippers", "notifications", "tracking", "livestreams", "settlement",
            "promotions", "analytics", "flashsales", "search");
    private static final Set<String> DENIED_AUTH_COMMANDS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/social-login",
            "/api/auth/refresh-token", "/api/auth/logout", "/api/auth/forgot-password",
            "/api/auth/reset-password", "/api/auth/email-verification/request",
            "/api/auth/email-verification/confirm");

    private ApiProxyPolicy() { }

    static boolean allows(HttpMethod method, String path) {
        if (method == null || !Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.PATCH, HttpMethod.DELETE).contains(method)
                || path == null || !path.startsWith("/api/")
                || path.contains("..") || path.contains("//") || path.indexOf('\\') >= 0) return false;
        if (path.contains("/internal/") || path.endsWith("/internal") || DENIED_AUTH_COMMANDS.contains(path)
                || path.startsWith("/api/auth/registrations/")) return false;
        if (path.startsWith("/api/auth/")) {
            return path.equals("/api/auth/sessions")
                    || path.matches("/api/auth/sessions/[^/]+")
                    || path.equals("/api/auth/firebase/chat-token")
                    || path.matches("/api/auth/admin/accounts/[0-9]+/(block|unblock)")
                    || path.matches("/api/auth/accounts/[0-9]+");
        }
        int slash = path.indexOf('/', 5);
        String resource = slash < 0 ? path.substring(5) : path.substring(5, slash);
        return RESOURCES.contains(resource);
    }
}
