package com.delivery.auth.application.api;
import com.delivery.auth.domain.model.SecurityTokenPurpose;
public interface SecurityEmailPort {
    void publish(Long accountId, String email, SecurityTokenPurpose purpose, String rawToken, String clientIp);
}
