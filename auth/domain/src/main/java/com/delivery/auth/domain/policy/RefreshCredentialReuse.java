package com.delivery.auth.domain.policy;

public final class RefreshCredentialReuse extends InvalidAuthToken {
    public RefreshCredentialReuse(String message) { super(message); }
}
