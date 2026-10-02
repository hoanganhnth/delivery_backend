package com.delivery.auth_service.service;
import com.delivery.auth.application.api.UserStatusProjectionPort;
import com.delivery.auth_service.config.*;
import com.delivery.auth_service.payload.BaseResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
@Component
@RequiredArgsConstructor
public class HttpUserStatusProjectionAdapter implements UserStatusProjectionPort {
    private final UserServiceConfig userServiceConfig;
    private final RestTemplate restTemplate;
    private final AuthUserCircuitBreaker userCircuitBreaker;
    @Override public void synchronize(Long userId, Long adminId, String reason, boolean blocked) {
        String url = userServiceConfig.getBlockStatusUrl(userId);
        java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("adminId", adminId);
        requestBody.put("blocked", blocked);
        if (blocked) {
            requestBody.put("reason", reason);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", requireInternalSecret());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<BaseResponse<Void>> response = callUserService(() -> restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    new ParameterizedTypeReference<BaseResponse<Void>>() {
                    }));
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || response.getBody().getStatus() != 1) {
                throw new IllegalStateException("User profile status synchronization was rejected");
            }
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to synchronize user profile block state", e);
        }
    }

    private <T> T callUserService(java.util.function.Supplier<T> supplier) {
        return userCircuitBreaker == null ? supplier.get() : userCircuitBreaker.execute(supplier);
    }

    private String requireInternalSecret() {
        String secret = userServiceConfig.getInternalSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SECRET is required for auth/user linkage");
        }
        return secret;
    }
}
