package com.delivery.auth.application.api;
public interface AccountLifecycleUseCase {
    void block(Long accountId, Long adminId, String reason);
    void unblock(Long accountId, Long adminId);
    void reconcile();
}
