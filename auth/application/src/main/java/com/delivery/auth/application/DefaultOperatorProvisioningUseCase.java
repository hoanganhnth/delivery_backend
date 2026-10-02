package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.policy.*;
import java.time.LocalDateTime;
import java.util.*;

/** Operator identities commit before profile handoff so failed handoffs can resume. */
public final class DefaultOperatorProvisioningUseCase implements OperatorProvisioningUseCase {
    private final AuthAccountPort accounts;
    private final PasswordCredentialPort credentials;
    private final UserProfileProvisioningUseCase profiles;
    public DefaultOperatorProvisioningUseCase(AuthAccountPort accounts, PasswordCredentialPort credentials,
            UserProfileProvisioningUseCase profiles) {
        this.accounts=Objects.requireNonNull(accounts);this.credentials=Objects.requireNonNull(credentials);
        this.profiles=Objects.requireNonNull(profiles);
    }
    @Override public AuthAccount provisionAdmin(String email,String password) { return provision(email,password,AuthAccount.Role.ADMIN); }
    @Override public AuthAccount provisionShipper(String email,String password) { return provision(email,password,AuthAccount.Role.SHIPPER); }
    private AuthAccount provision(String email,String password,AuthAccount.Role role) {
        String prefix=role==AuthAccount.Role.ADMIN ? "Operator-provisioned admin " : "Operator-provisioned ";
        if(email==null || email.isBlank()) throw new IllegalArgumentException(prefix+"email is required");
        if(password==null || password.isBlank()) throw new IllegalArgumentException(prefix+"password is required");
        String canonical=email.trim().toLowerCase(Locale.ROOT);
        AuthAccount account=accounts.findByEmail(canonical).map(existing -> resume(existing,canonical,password,role))
                .orElseGet(() -> accounts.createOrResume(new AuthAccount(null,null,AuthAccount.LifecycleStatus.PENDING_PROFILE,0L,
                        canonical,credentials.hash(password),role,true,false,LocalDateTime.now(),false,0L,null,null,0,null,
                        false,null,null,0L,null,null,null),winner -> resume(winner,canonical,password,role)));
        return account.userId()==null ? accounts.save(profiles.provision(account)) : account;
    }
    private AuthAccount resume(AuthAccount account,String email,String password,AuthAccount.Role role) {
        if(account.role()!=role || !credentials.matches(password,account.passwordHash())) {
            throw new RegistrationIdentityConflict("Email already registered: "+email);
        }
        if(!Boolean.TRUE.equals(account.isActive())) throw new CredentialsRejected("Account is blocked or inactive");
        return account;
    }
}
