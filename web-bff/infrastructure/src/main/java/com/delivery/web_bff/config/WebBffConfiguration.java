package com.delivery.web_bff.config;

import com.delivery.web_bff.application.api.Ports;
import com.delivery.web_bff.application.api.UseCases;
import com.delivery.web_bff.auth.LoginApplicationService;
import com.delivery.web_bff.proxy.ApiProxyPolicyApplicationService;
import com.delivery.web_bff.proxy.ProxyForwardingApplicationService;
import com.delivery.web_bff.session.AccessTokenApplicationService;
import com.delivery.web_bff.session.CurrentSessionApplicationService;
import com.delivery.web_bff.session.LogoutApplicationService;
import com.delivery.web_bff.session.RefreshApplicationService;
import com.delivery.web_bff.domain.session.SessionFactory;
import com.delivery.web_bff.infrastructure.session.TokenVault;
import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class WebBffConfiguration {
    @Bean
    SessionFactory sessionFactory(TokenVault vault,
            @Value("${web-bff.session-max-age-seconds}") long maxAgeSeconds) {
        return new SessionFactory(vault, size -> { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; },
                Duration.ofSeconds(maxAgeSeconds));
    }

    @Bean
    Ports.Clock webBffPortsClock(java.time.Clock clock) {
        return clock::instant;
    }

    @Bean UseCases.Login login(Ports.Authentication auth, SessionFactory factory, Ports.Sessions sessions, Ports.Clock clock) {
        return new LoginApplicationService(auth, factory, sessions, clock);
    }
    @Bean UseCases.CurrentSession currentSession(Ports.Sessions sessions, Ports.Clock clock) {
        return new CurrentSessionApplicationService(sessions, clock);
    }
    @Bean UseCases.Refresh refresh(Ports.Sessions sessions, Ports.Authentication auth, Ports.TokenProtection tokens, Ports.Clock clock) {
        return new RefreshApplicationService(sessions, auth, tokens, clock);
    }
    @Bean UseCases.Logout logout(Ports.Sessions sessions, Ports.Authentication auth, Ports.TokenProtection tokens, Ports.Clock clock) {
        return new LogoutApplicationService(sessions, auth, tokens, clock);
    }
    @Bean UseCases.AccessTokenResolution accessToken(Ports.Sessions sessions, Ports.TokenProtection tokens, Ports.Clock clock) {
        return new AccessTokenApplicationService(sessions, tokens, clock);
    }
    @Bean UseCases.ApiPolicyEvaluation apiPolicy() { return new ApiProxyPolicyApplicationService(); }
    @Bean UseCases.ProxyForwarding proxyForwarding(UseCases.ApiPolicyEvaluation policy,
            UseCases.AccessTokenResolution access, Ports.ProxyForwarding gateway) {
        return new ProxyForwardingApplicationService(policy, access, gateway);
    }

    @Bean
    OncePerRequestFilter webBffOriginFilter(
            @Value("${web-bff.allowed-origins}") String allowedOrigins) {
        return new WebBffOriginFilter(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
