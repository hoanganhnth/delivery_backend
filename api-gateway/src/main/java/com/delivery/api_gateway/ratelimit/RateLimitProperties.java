package com.delivery.api_gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean trustedProxy = false;
    private List<String> trustedProxyCidrs = new ArrayList<>();
    private long windowSeconds = 60;
    private long redisTimeoutMillis = 500;
    private Group publicAuth = new Group(10, false);
    private Group publicCatalog = new Group(120, true);
    private Group authenticatedRead = new Group(300, true);
    private Group mutation = new Group(30, false);
    private Group websocketConnection = new Group(10, false);

    public static class Group {
        private int limit;
        private boolean failOpen;

        public Group() { }
        public Group(int limit, boolean failOpen) { this.limit = limit; this.failOpen = failOpen; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public boolean isFailOpen() { return failOpen; }
        public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isTrustedProxy() { return trustedProxy; }
    public void setTrustedProxy(boolean trustedProxy) { this.trustedProxy = trustedProxy; }
    public List<String> getTrustedProxyCidrs() { return trustedProxyCidrs; }
    public void setTrustedProxyCidrs(List<String> trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs == null ? new ArrayList<>() : new ArrayList<>(trustedProxyCidrs);
    }
    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
    public long getRedisTimeoutMillis() { return redisTimeoutMillis; }
    public void setRedisTimeoutMillis(long redisTimeoutMillis) { this.redisTimeoutMillis = redisTimeoutMillis; }
    public Group getPublicAuth() { return publicAuth; }
    public void setPublicAuth(Group value) { this.publicAuth = value; }
    public Group getPublicCatalog() { return publicCatalog; }
    public void setPublicCatalog(Group value) { this.publicCatalog = value; }
    public Group getAuthenticatedRead() { return authenticatedRead; }
    public void setAuthenticatedRead(Group value) { this.authenticatedRead = value; }
    public Group getMutation() { return mutation; }
    public void setMutation(Group value) { this.mutation = value; }
    public Group getWebsocketConnection() { return websocketConnection; }
    public void setWebsocketConnection(Group value) { this.websocketConnection = value; }
}
