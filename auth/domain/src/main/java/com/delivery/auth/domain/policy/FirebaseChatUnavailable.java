package com.delivery.auth.domain.policy;

public final class FirebaseChatUnavailable extends RuntimeException {
    public FirebaseChatUnavailable(String message) { super(message); }
    public FirebaseChatUnavailable(String message, Throwable cause) { super(message, cause); }
}
