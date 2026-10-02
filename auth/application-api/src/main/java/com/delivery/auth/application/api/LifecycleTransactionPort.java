package com.delivery.auth.application.api;
public interface LifecycleTransactionPort {
    void afterCommit(Runnable action);
    <T> T requiresNew(java.util.function.Supplier<T> action);
}
