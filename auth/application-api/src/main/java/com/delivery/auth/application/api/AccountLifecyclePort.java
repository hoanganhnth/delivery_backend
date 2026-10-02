package com.delivery.auth.application.api;
import com.delivery.auth.domain.model.AuthAccount;
import java.time.LocalDateTime;
import java.util.List;
public interface AccountLifecyclePort extends AuthAccountLockPort {
    List<AuthAccount> pending(int limit);
    int clearPending(Long id, Long version, LocalDateTime at);
    void recordFailure(Long id, Long version, String message, LocalDateTime at);
    void revokeCredentials(Long id, LocalDateTime at);
    void statusChanged(AuthAccount account, Long adminId, String reasonCode);
}
