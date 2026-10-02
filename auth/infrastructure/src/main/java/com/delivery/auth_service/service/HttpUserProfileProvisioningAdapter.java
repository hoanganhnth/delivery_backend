package com.delivery.auth_service.service;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth_service.config.AuthUserCircuitBreaker;
import com.delivery.auth_service.config.UserServiceConfig;
import com.delivery.auth_service.dto.CreateUserRequest;
import com.delivery.auth_service.dto.UserResponse;
import com.delivery.auth_service.payload.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
public class HttpUserProfileProvisioningAdapter implements UserProfileProvisioningPort {
    private final UserServiceConfig config;
    private final RestTemplate http;
    private final AuthUserCircuitBreaker circuit;
    @Override public UserProfileProvisioningReply provision(AuthAccount account) {
        String secret = config.getInternalSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for auth/user linkage");
        }
        HttpHeaders headers = new HttpHeaders(); headers.set("Internal-Token", secret);
        var request = new HttpEntity<>(new CreateUserRequest(account.id(), account.id(), account.email(), account.role().name()), headers);
        java.util.function.Supplier<ResponseEntity<BaseResponse<UserResponse>>> exchange = () -> http.exchange(
                config.getRegisterUrl(), HttpMethod.POST, request, new ParameterizedTypeReference<BaseResponse<UserResponse>>() {});
        try {
            var response = (circuit == null ? exchange.get() : circuit.execute(exchange)).getBody();
            if (response == null) return null;
            var user = response.getData();
            return new UserProfileProvisioningReply(response.getStatus(), response.getMessage(), user == null ? null : user.getId(),
                    user == null ? null : user.getAuthId(), user == null ? null : user.getEmail(), user == null ? null : user.getRole());
        } catch (RestClientException error) {
            log.error("User profile provisioning failed for authAccountId={}", account.id(), error);
            throw new IllegalStateException("Failed to provision user profile", error);
        }
    }
}
