package com.delivery.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PrincipalLookupServiceTest {

    private final AuthAccountRepository repository = mock(AuthAccountRepository.class);
    private final PrincipalLookupService service = new PrincipalLookupService(repository);

    @Test
    void mapsAuthAccountToStableIdentityContract() {
        long principalId = 40L;
        for (AuthAccount.Role role : AuthAccount.Role.values()) {
            AuthAccount account = new AuthAccount();
            org.springframework.test.util.ReflectionTestUtils.setField(account, "id", principalId);
            account.setRole(role);
            account.setLifecycleStatus(IdentityLifecycleStatus.ACTIVE);
            when(repository.findById(principalId)).thenReturn(Optional.of(account));

            var principal = service.findByPrincipalId(principalId).orElseThrow();

            assertThat(principal.principalId()).isEqualTo(principalId);
            assertThat(principal.role()).isEqualTo(IdentityRole.valueOf(role.name()));
            assertThat(principal.lifecycleStatus()).isEqualTo(IdentityLifecycleStatus.ACTIVE);
            principalId++;
        }
    }

    @Test
    void missingAccountStaysMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.findByPrincipalId(99L)).isEmpty();
    }
}
