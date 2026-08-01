package com.delivery.user_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth-service")
public class AuthServiceConfig {
    private String url;
    private String internalSecret;
    private long timeoutMs = 2000;

    public String getResolveRegistrationUrl() {
        return url + "/api/auth/internal/registrations/resolve";
    }

    public String getCompleteRegistrationUrl() {
        return url + "/api/auth/internal/registrations/complete";
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getInternalSecret() { return internalSecret; }
    public void setInternalSecret(String internalSecret) { this.internalSecret = internalSecret; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}
