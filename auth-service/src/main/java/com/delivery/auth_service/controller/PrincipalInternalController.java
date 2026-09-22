package com.delivery.auth_service.controller;

import com.delivery.auth_service.exception.AccessDeniedException;
import com.delivery.auth_service.service.PrincipalLookupService;
import com.delivery.identity.contracts.IdentityPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/internal/principals")
public class PrincipalInternalController {

    private final PrincipalLookupService principalLookupService;
    private final String internalSecret;

    public PrincipalInternalController(
            PrincipalLookupService principalLookupService,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.principalLookupService = principalLookupService;
        this.internalSecret = internalSecret == null ? "" : internalSecret;
    }

    @GetMapping("/{principalId}")
    public ResponseEntity<IdentityPrincipal> findByPrincipalId(
            @RequestHeader(value = "Internal-Token", required = false) String suppliedSecret,
            @PathVariable String principalId) {
        authorize(suppliedSecret);
        return principalLookupService.findByPrincipalId(parsePrincipalId(principalId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private long parsePrincipalId(String value) {
        try {
            long principalId = Long.parseLong(value);
            if (principalId <= 0) {
                throw new IllegalArgumentException("principalId must be positive");
            }
            return principalId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("principalId must be a positive integer", exception);
        }
    }

    private void authorize(String suppliedSecret) {
        if (internalSecret.isBlank() || suppliedSecret == null
                || !MessageDigest.isEqual(
                        internalSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("principal lookup unauthorized");
        }
    }
}
