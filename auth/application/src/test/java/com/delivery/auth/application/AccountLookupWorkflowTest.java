package com.delivery.auth.application;
import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AccountLookupWorkflowTest {
    @Test void canonicalEmailLookupUsesSafeProjectionAndRejectsAbsentIdentity() {
        class Fake implements AuthAccountPort {
            String email; Long id;
            AuthAccount account=AccountLifecycleWorkflowTest.account(7L,70L,true,false,AuthAccount.LifecycleStatus.ACTIVE,2L,0L,false);
            public Optional<AuthAccount> findByEmail(String value) {email=value;return Optional.of(account);}
            public Optional<AuthAccount> findById(Long value) {id=value;return value==7L?Optional.of(account):Optional.empty();}
            public AuthAccount save(AuthAccount value){throw new UnsupportedOperationException();}
            public AuthAccount createOrResume(AuthAccount value,Consumer<AuthAccount> winner){throw new UnsupportedOperationException();}
        }
        Fake repo=new Fake();var lookup=new DefaultAccountLookupUseCase(repo);
        assertThat(lookup.byEmail(null)).isEmpty();assertThat(lookup.byEmail(" ")).isEmpty();
        assertThat(repo.email).isNull();
        var selected=lookup.byEmail(" USER@Example.TEST ").orElseThrow();
        assertThat(repo.email).isEqualTo("user@example.test");
        assertThat(selected.id()).isEqualTo(7L);
        assertThat(selected.role()).isEqualTo(AuthAccount.Role.USER);
        assertThat(selected.userId()).isEqualTo(70L);
        assertThat(lookup.requireById(7L)).isEqualTo(selected);
        assertThat(lookup.byId(8L)).isEmpty();
        assertThatThrownBy(()->lookup.requireById(8L)).hasMessage("Account not found with id: 8");
    }
}
