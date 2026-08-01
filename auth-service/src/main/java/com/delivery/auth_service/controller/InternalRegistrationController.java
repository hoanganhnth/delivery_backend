package com.delivery.auth_service.controller;

import com.delivery.auth_service.dto.CompleteUserProvisioningRequest;
import com.delivery.auth_service.dto.UserProvisioningIdentityResponse;
import com.delivery.auth_service.dto.UserProvisioningTokenRequest;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.auth_service.service.AccountSecurityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/internal/registrations")
public class InternalRegistrationController {
    private final AccountSecurityService accountSecurityService;
    private final String internalSecret;

    public InternalRegistrationController(
            AccountSecurityService accountSecurityService,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.accountSecurityService = accountSecurityService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/resolve")
    public ResponseEntity<BaseResponse<UserProvisioningIdentityResponse>> resolve(
            @Valid @RequestBody UserProvisioningTokenRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403).body(BaseResponse.failure("Forbidden"));
        }
        return ResponseEntity.ok(BaseResponse.success(
                accountSecurityService.resolveUserProvisioning(request.getProvisioningToken()),
                "User provisioning identity resolved"));
    }

    @PostMapping("/complete")
    public ResponseEntity<BaseResponse<Void>> complete(
            @Valid @RequestBody CompleteUserProvisioningRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403).body(BaseResponse.failure("Forbidden"));
        }
        accountSecurityService.completeUserProvisioning(
                request.getProvisioningToken(), request.getUserId());
        return ResponseEntity.ok(BaseResponse.success(null, "User provisioning completed"));
    }

    private boolean isInternalRequest(String token) {
        return internalSecret != null && !internalSecret.isBlank()
                && token != null && token.equals(internalSecret);
    }
}
