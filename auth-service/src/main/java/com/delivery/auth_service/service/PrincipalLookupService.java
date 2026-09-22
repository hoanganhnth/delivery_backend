package com.delivery.auth_service.service;

import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.identity.contracts.IdentityPrincipal;
import com.delivery.identity.contracts.IdentityRole;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrincipalLookupService {

    private final AuthAccountRepository authAccountRepository;

    public PrincipalLookupService(AuthAccountRepository authAccountRepository) {
        this.authAccountRepository = authAccountRepository;
    }

    @Transactional(readOnly = true)
    public Optional<IdentityPrincipal> findByPrincipalId(Long principalId) {
        return authAccountRepository.findById(principalId)
                .map(account -> new IdentityPrincipal(
                        account.getId(),
                        toContractRole(account.getRole()),
                        account.getLifecycleStatus()));
    }

    private IdentityRole toContractRole(AuthAccount.Role role) {
        return switch (role) {
            case USER -> IdentityRole.USER;
            case ADMIN -> IdentityRole.ADMIN;
            case SHIPPER -> IdentityRole.SHIPPER;
            case SHOP_OWNER -> IdentityRole.SHOP_OWNER;
        };
    }
}
