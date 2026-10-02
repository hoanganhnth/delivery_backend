package com.delivery.auth_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component // ✅ dùng Component thay vì Configuration
@ConfigurationProperties(prefix = "user-service")
public class UserServiceConfig {
    private String url;
    private String internalSecret;
    private long timeoutMs = 2000;
    private Circuit circuit = new Circuit();

    public static class Circuit {
        private float failureRateThreshold = 50;
        private int slidingWindowSize = 20;
        private long waitOpenSeconds = 30;
        private int permittedHalfOpenCalls = 5;

        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        public long getWaitOpenSeconds() { return waitOpenSeconds; }
        public void setWaitOpenSeconds(long waitOpenSeconds) { this.waitOpenSeconds = waitOpenSeconds; }
        public int getPermittedHalfOpenCalls() { return permittedHalfOpenCalls; }
        public void setPermittedHalfOpenCalls(int permittedHalfOpenCalls) { this.permittedHalfOpenCalls = permittedHalfOpenCalls; }
    }

    public String getUrl() {
        return url;
    }

    // get right url register user
    public String getRegisterUrl() {
        return url + "/api/users"; // ✅ thêm /api/users/register để trỏ đúng endpoint
    }

    public String getBlockStatusUrl(Long userId) {
        return url + "/api/internal/users/" + userId + "/block-status";
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public Circuit getCircuit() { return circuit; }
    public void setCircuit(Circuit circuit) { this.circuit = circuit; }
}
