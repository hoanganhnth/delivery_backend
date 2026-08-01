package com.delivery.user_service.service;

import com.delivery.user_service.config.AuthServiceConfig;
import com.delivery.user_service.dto.AuthProvisioningCompleteRequest;
import com.delivery.user_service.dto.AuthProvisioningIdentityResponse;
import com.delivery.user_service.dto.AuthProvisioningTokenRequest;
import com.delivery.user_service.dto.AuthServiceResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthRegistrationClient {
    private final RestTemplate restTemplate;
    private final AuthServiceConfig config;

    public AuthRegistrationClient(RestTemplate restTemplate, AuthServiceConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public AuthProvisioningIdentityResponse resolve(String provisioningToken) {
        try {
            ResponseEntity<AuthServiceResponse<AuthProvisioningIdentityResponse>> response =
                    restTemplate.exchange(
                            config.getResolveRegistrationUrl(),
                            HttpMethod.POST,
                            internalRequest(new AuthProvisioningTokenRequest(provisioningToken)),
                            new ParameterizedTypeReference<>() {});
            AuthServiceResponse<AuthProvisioningIdentityResponse> body = response.getBody();
            if (body == null || body.getStatus() != 1 || body.getData() == null
                    || body.getData().getAuthId() == null
                    || body.getData().getEmail() == null
                    || body.getData().getRole() == null) {
                throw unavailable("Auth returned an invalid provisioning identity", null);
            }
            return body.getData();
        } catch (HttpStatusCodeException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw unavailable("Auth registration service is unavailable", e);
        }
    }

    public void complete(String provisioningToken, Long userId) {
        try {
            ResponseEntity<AuthServiceResponse<Void>> response = restTemplate.exchange(
                    config.getCompleteRegistrationUrl(),
                    HttpMethod.POST,
                    internalRequest(new AuthProvisioningCompleteRequest(provisioningToken, userId)),
                    new ParameterizedTypeReference<>() {});
            AuthServiceResponse<Void> body = response.getBody();
            if (body == null || body.getStatus() != 1) {
                throw unavailable("Auth did not complete user linkage", null);
            }
        } catch (HttpStatusCodeException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw unavailable("Auth registration service is unavailable", e);
        }
    }

    private <T> HttpEntity<T> internalRequest(T body) {
        String secret = config.getInternalSecret();
        if (secret == null || secret.isBlank()) {
            throw unavailable("Auth registration service is unavailable", null);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Internal-Token", secret);
        return new HttpEntity<>(body, headers);
    }

    private ResponseStatusException translate(HttpStatusCodeException error) {
        if (error.getStatusCode().value() == 401) {
            return new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid or expired provisioning token", error);
        }
        if (error.getStatusCode().value() == 409) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT, "Registration identity conflict", error);
        }
        return unavailable("Auth registration service is unavailable", error);
    }

    private ResponseStatusException unavailable(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
