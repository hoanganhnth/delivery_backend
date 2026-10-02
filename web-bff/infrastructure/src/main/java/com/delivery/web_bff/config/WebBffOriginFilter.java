package com.delivery.web_bff.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Browser boundary for the BFF. Authentication is cookie based, so every
 * state-changing browser request must prove the exact trusted origin.
 */
public final class WebBffOriginFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final Set<String> allowedOrigins;

    public WebBffOriginFilter(Set<String> allowedOrigins) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/bff/")
                && MUTATING_METHODS.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin == null || !allowedOrigins.contains(origin)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin is not allowed");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
