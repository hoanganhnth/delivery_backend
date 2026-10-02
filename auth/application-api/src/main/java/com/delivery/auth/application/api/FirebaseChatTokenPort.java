package com.delivery.auth.application.api;

public interface FirebaseChatTokenPort {
    boolean available();
    String issue(String uid, java.util.Map<String, Object> claims);
}
