package com.delivery.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private boolean enabled = false;
    private String apiToken = "";
    private String gatewayBaseUrl = "http://localhost:8079";
    private boolean allowNonLocalTargets = false;
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5174", "http://127.0.0.1:5174",
            "http://localhost:4174", "http://127.0.0.1:4174"));
    private List<String> allowedGatewayHosts = new ArrayList<>(List.of(
            "localhost", "127.0.0.1", "api-gateway"));
    private int pollIntervalMillis = 1000;
    private int humanOrderTimeoutSeconds = 300;
    private int runTimeoutSeconds = 900;
    private int maxShippers = 50;
    private int maxConcurrentRuns = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken == null ? "" : apiToken;
    }

    public String getGatewayBaseUrl() {
        return gatewayBaseUrl;
    }

    public void setGatewayBaseUrl(String gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl == null ? "" : gatewayBaseUrl;
    }

    public boolean isAllowNonLocalTargets() {
        return allowNonLocalTargets;
    }

    public void setAllowNonLocalTargets(boolean allowNonLocalTargets) {
        this.allowNonLocalTargets = allowNonLocalTargets;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? new ArrayList<>()
                : new ArrayList<>(allowedOrigins);
    }

    public List<String> getAllowedGatewayHosts() {
        return allowedGatewayHosts;
    }

    public void setAllowedGatewayHosts(List<String> allowedGatewayHosts) {
        this.allowedGatewayHosts = allowedGatewayHosts == null
                ? new ArrayList<>()
                : new ArrayList<>(allowedGatewayHosts);
    }

    public int getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public void setPollIntervalMillis(int pollIntervalMillis) {
        this.pollIntervalMillis = Math.max(200, pollIntervalMillis);
    }

    public int getHumanOrderTimeoutSeconds() {
        return humanOrderTimeoutSeconds;
    }

    public void setHumanOrderTimeoutSeconds(int humanOrderTimeoutSeconds) {
        this.humanOrderTimeoutSeconds = Math.max(5, humanOrderTimeoutSeconds);
    }

    public int getRunTimeoutSeconds() {
        return runTimeoutSeconds;
    }

    public void setRunTimeoutSeconds(int runTimeoutSeconds) {
        this.runTimeoutSeconds = Math.max(10, runTimeoutSeconds);
    }

    public int getMaxShippers() {
        return maxShippers;
    }

    public void setMaxShippers(int maxShippers) {
        this.maxShippers = Math.max(1, Math.min(50, maxShippers));
    }

    public int getMaxConcurrentRuns() {
        return maxConcurrentRuns;
    }

    public void setMaxConcurrentRuns(int maxConcurrentRuns) {
        this.maxConcurrentRuns = Math.max(1, Math.min(16, maxConcurrentRuns));
    }
}
