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

    // get right url register user
    public String getRegisterUrl() {
        return url + "/api/users"; // ✅ thêm /api/users/register để trỏ đúng endpoint
    }

    public String getBlockUserUrl(Long userId) {
        return url + "/api/users/admin/" + userId + "/block";
    }

    public String getUnblockUserUrl(Long userId) {
        return url + "/api/users/admin/" + userId + "/unblock";
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
