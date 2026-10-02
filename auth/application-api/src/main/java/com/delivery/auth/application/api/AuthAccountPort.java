package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import java.util.Optional;

/** Persistence boundary for Auth account facts and mutations. */
public interface AuthAccountPort {

    Optional<AuthAccount> findByEmail(String email);

    Optional<AuthAccount> findById(Long accountId);

    AuthAccount save(AuthAccount account);

    /** Insert or return a concurrent identity; the caller validates the winner without losing its transaction. */
    AuthAccount createOrResume(AuthAccount account, java.util.function.Consumer<AuthAccount> verifyWinner);
}
