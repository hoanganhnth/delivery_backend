package com.delivery.auth.domain.policy;

/** Framework-independent registration policy failure. */
public final class RegistrationPolicyException extends IllegalArgumentException {

    private final RegistrationRuleViolation violation;

    public RegistrationPolicyException(RegistrationRuleViolation violation) {
        this(violation, null);
    }

    public RegistrationPolicyException(
            RegistrationRuleViolation violation, Throwable cause) {
        super(violation.name(), cause);
        this.violation = violation;
    }

    public RegistrationRuleViolation violation() {
        return violation;
    }
}
