package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import java.util.Optional;

/** Persistence boundary for Auth account facts and mutations. */
public interface AuthAccountPort {

    Optional<AuthAccount> findByEmail(String email);

    Optional<AuthAccount> findById(Long accountId);

    AuthAccount save(AuthAccount account);
}
