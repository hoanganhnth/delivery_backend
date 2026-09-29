package com.delivery.auth.domain.policy;

/** Structured failure reasons for public registration admission. */
public enum RegistrationRuleViolation {
    ROLE_REQUIRED,
    INVALID_ROLE,
    ROLE_NOT_PUBLIC
}
