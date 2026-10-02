package com.delivery.auth.application.api;
import java.util.Optional;
public interface AccountLookupUseCase {
    Optional<AccountSnapshot> byId(Long id);
    AccountSnapshot requireById(Long id);
    Optional<AccountSnapshot> byEmail(String email);
}
