package com.delivery.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Safe-by-default feature flags shared by Phase 8 service rollouts. */
@ConfigurationProperties(prefix = "app.phase8.rollout")
public class Phase8RolloutProperties {
    private boolean shadowReads;
    private boolean writesEnabled;
    private int trafficPercentage;

    public boolean isShadowReads() { return shadowReads; }
    public void setShadowReads(boolean shadowReads) { this.shadowReads = shadowReads; }
    public boolean isWritesEnabled() { return writesEnabled; }
    public void setWritesEnabled(boolean writesEnabled) { this.writesEnabled = writesEnabled; }
    public int getTrafficPercentage() { return trafficPercentage; }

    public void setTrafficPercentage(int trafficPercentage) {
        if (trafficPercentage < 0 || trafficPercentage > 100) {
            throw new IllegalArgumentException("trafficPercentage must be between 0 and 100");
        }
        this.trafficPercentage = trafficPercentage;
    }
}
