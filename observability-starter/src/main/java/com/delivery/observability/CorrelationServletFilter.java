package com.delivery.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class CorrelationServletFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        try (CorrelationContext ignored = CorrelationContext.with(httpRequest.getHeader(CorrelationId.HEADER))) {
            httpResponse.setHeader(CorrelationId.HEADER, CorrelationContext.currentOrCreate());
            chain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("Invalid X-Correlation-Id");
        }
    }
}
