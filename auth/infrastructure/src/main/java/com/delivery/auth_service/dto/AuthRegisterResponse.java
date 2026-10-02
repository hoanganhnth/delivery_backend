package com.delivery.auth_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.delivery.identity.contracts.IdentityLifecycleStatus;

@Data
@NoArgsConstructor
public class AuthRegisterResponse {
    private Long authId;
    /** Canonical Auth-owned identity key; authId remains a compatibility alias. */
    private Long principalId;
    private String email;
    private String role;
    private String provisioningToken;
    private String registrationHandle;
    private LocalDateTime expiresAt;
    private IdentityLifecycleStatus lifecycleStatus;

    public AuthRegisterResponse(Long authId, String email, String role, String provisioningToken,
            String registrationHandle, LocalDateTime expiresAt, IdentityLifecycleStatus lifecycleStatus) {
        this.authId = authId; this.principalId = authId; this.email = email; this.role = role; this.provisioningToken = provisioningToken;
        this.registrationHandle = registrationHandle; this.expiresAt = expiresAt; this.lifecycleStatus = lifecycleStatus;
    }
}
