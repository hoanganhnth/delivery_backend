package com.delivery.user.application.api;

/** Verified transport identity mapped without a framework dependency. */
public record UserActor(Long principalId, boolean customer, boolean admin) {}
