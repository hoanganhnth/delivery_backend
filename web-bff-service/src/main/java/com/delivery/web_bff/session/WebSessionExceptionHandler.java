package com.delivery.web_bff.session;

import com.delivery.web_bff.auth.AuthGateway;
import com.delivery.web_bff.proxy.ApiProxyService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WebSessionExceptionHandler {
    @ExceptionHandler(AuthGateway.AuthenticationRejectedException.class)
    ResponseEntity<Map<String, Object>> authenticationRejected(
            AuthGateway.AuthenticationRejectedException exception) {
        return ResponseEntity.status(401).body(Map.of("error", Map.of(
                "code", "AUTHENTICATION_REJECTED", "message", exception.getMessage())));
    }

    @ExceptionHandler(WebSessionService.SessionRejectedException.class)
    ResponseEntity<Map<String, Object>> rejected(WebSessionService.SessionRejectedException exception) {
        return ResponseEntity.status(401).body(Map.of("error", Map.of(
                "code", "SESSION_REJECTED", "message", exception.getMessage())));
    }

    @ExceptionHandler(ApiProxyService.ApiProxyRejectedException.class)
    ResponseEntity<Map<String, Object>> proxyRejected(ApiProxyService.ApiProxyRejectedException exception) {
        return ResponseEntity.status(404).body(Map.of("error", Map.of(
                "code", "BFF_ROUTE_NOT_ALLOWED", "message", exception.getMessage())));
    }

    @ExceptionHandler(WebSessionRefreshService.RefreshRejectedException.class)
    ResponseEntity<Map<String, Object>> refreshBusy(WebSessionRefreshService.RefreshRejectedException exception) {
        return ResponseEntity.status(409).body(Map.of("error", Map.of(
                "code", "REFRESH_IN_PROGRESS", "message", exception.getMessage())));
    }
}
