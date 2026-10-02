package com.delivery.auth.application.api;
import com.delivery.auth.domain.model.AuthAccount;
public interface AuthAccountLockPort {
    Change updateLocked(Long id, java.util.function.UnaryOperator<AuthAccount> update);
    record Change(AuthAccount before, AuthAccount after) {}
}
