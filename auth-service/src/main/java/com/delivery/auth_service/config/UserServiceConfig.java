package com.delivery.auth_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component // ✅ dùng Component thay vì Configuration
@ConfigurationProperties(prefix = "user-service")
public class UserServiceConfig {
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
