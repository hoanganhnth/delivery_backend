package com.delivery.search.application.api;

/** Side effects owned by the host. Fingerprinting occurs only after admission. */
public interface ProjectionPorts {
    enum Claim { APPLY, EXACT_REPLAY, STALE }
    String fingerprint(ProjectionInput input);
    Claim claim(ProjectionInput input, String fingerprint);
    void write(ProjectionInput input);
    void stale(ProjectionInput input);
    void received(ProjectionInput input);
    void tombstoneApplied();
    void replayFailed(ProjectionInput input, Exception failure);
}
