package com.delivery.web_bff.session;

import com.delivery.web_bff.application.api.UseCases;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WebSessionExceptionHandler {
    @ExceptionHandler(UseCases.AuthenticationRejectedException.class)
    ResponseEntity<Map<String, Object>> authenticationRejected(UseCases.AuthenticationRejectedException exception) {
        return ResponseEntity.status(401).body(Map.of("error", Map.of(
                "code", "AUTHENTICATION_REJECTED", "message", exception.getMessage())));
    }

    @ExceptionHandler(UseCases.SessionRejectedException.class)
    ResponseEntity<Map<String, Object>> rejected(UseCases.SessionRejectedException exception) {
        return ResponseEntity.status(401).body(Map.of("error", Map.of(
                "code", "SESSION_REJECTED", "message", exception.getMessage())));
    }

    @ExceptionHandler(UseCases.ApiProxyRejectedException.class)
    ResponseEntity<Map<String, Object>> proxyRejected(UseCases.ApiProxyRejectedException exception) {
        return ResponseEntity.status(404).body(Map.of("error", Map.of(
                "code", "BFF_ROUTE_NOT_ALLOWED", "message", exception.getMessage())));
    }

    @ExceptionHandler(UseCases.RefreshInProgressException.class)
    ResponseEntity<Map<String, Object>> refreshBusy(UseCases.RefreshInProgressException exception) {
        return ResponseEntity.status(409).body(Map.of("error", Map.of(
                "code", "REFRESH_IN_PROGRESS", "message", exception.getMessage())));
    }
}
