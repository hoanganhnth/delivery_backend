package com.delivery.web_bff.session;

import com.delivery.web_bff.domain.session.ApiProxyRejectedException;
import com.delivery.web_bff.domain.session.AuthenticationRejectedException;
import com.delivery.web_bff.domain.session.RefreshInProgressException;
import com.delivery.web_bff.domain.session.SessionRejectedException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WebSessionExceptionHandler {
    @ExceptionHandler(AuthenticationRejectedException.class)
    ResponseEntity<Map<String, Object>> authenticationRejected(AuthenticationRejectedException exception) {
        return ResponseEntity.status(401).body(Map.of("error", Map.of(
                "code", "AUTHENTICATION_REJECTED", "message", exception.getMessage())));
    }

    @ExceptionHandler(SessionRejectedException.class)
    ResponseEntity<Map<String, Object>> rejected(SessionRejectedException exception) {
        return ResponseEntity.status(401).body(Map.of("error", Map.of(
                "code", "SESSION_REJECTED", "message", exception.getMessage())));
    }

    @ExceptionHandler(ApiProxyRejectedException.class)
    ResponseEntity<Map<String, Object>> proxyRejected(ApiProxyRejectedException exception) {
        return ResponseEntity.status(404).body(Map.of("error", Map.of(
                "code", "BFF_ROUTE_NOT_ALLOWED", "message", exception.getMessage())));
    }

    @ExceptionHandler(RefreshInProgressException.class)
    ResponseEntity<Map<String, Object>> refreshBusy(RefreshInProgressException exception) {
        return ResponseEntity.status(409).body(Map.of("error", Map.of(
                "code", "REFRESH_IN_PROGRESS", "message", exception.getMessage())));
    }
}
