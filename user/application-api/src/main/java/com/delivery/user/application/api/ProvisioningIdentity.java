package com.delivery.user.application.api;

/** Immutable identity already validated by the signed-handoff adapter. */
public record ProvisioningIdentity(Long principalId, String email, String role) { }
