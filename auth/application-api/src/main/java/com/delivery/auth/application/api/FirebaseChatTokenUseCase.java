package com.delivery.auth.application.api;

public interface FirebaseChatTokenUseCase {
    Result issue(Actor actor);
    record Actor(Long principalId, java.util.Set<String> roles, boolean supportAgent) {}
    record Result(String token, long expiresInSeconds, Long principalId, String role) {}
}
