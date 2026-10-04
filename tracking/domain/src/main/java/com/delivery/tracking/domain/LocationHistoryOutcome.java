package com.delivery.tracking.domain;
/** PENDING exists only inside the owning transaction, never as a committed outcome. */
public enum LocationHistoryOutcome { PENDING, PERSISTED, SAMPLED_OUT, NO_DELIVERY, OFFLINE_TOMBSTONE }
