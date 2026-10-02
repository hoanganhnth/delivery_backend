package com.delivery.auth_service.service;
import com.delivery.auth.application.api.SecurityEmailPort;
import com.delivery.auth.domain.model.SecurityTokenPurpose;
import com.delivery.auth_service.entity.AuthSecurityToken;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public final class SecurityEmailEventAdapter implements SecurityEmailPort {
    private final ApplicationEventPublisher publisher;
    @Override public void publish(Long id,String email,SecurityTokenPurpose purpose,String raw,String ip) {
        publisher.publishEvent(new SecurityEmailEvent(id,email,AuthSecurityToken.Purpose.valueOf(purpose.name()),raw,ip));
    }
}
