package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistrationRecoveryWorkflowTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    @Test void preservesNextActionAndProfileLinkForEveryLifecycle() {
        var expected = Map.of(AuthAccount.LifecycleStatus.PENDING_PROFILE,"CREATE_PROFILE",
                AuthAccount.LifecycleStatus.PENDING_EMAIL_VERIFICATION,"VERIFY_EMAIL",
                AuthAccount.LifecycleStatus.ACTIVE,"LOGIN",AuthAccount.LifecycleStatus.BLOCKED,"CONTACT_SUPPORT");
        Fake f = new Fake();
        for (var entry : expected.entrySet()) {
            f.status=entry.getKey();f.userId=null;
            var result=f.useCase(1).status("raw");
            assertThat(result.nextAction()).isEqualTo(entry.getValue());
            assertThat(result.status()).isEqualTo(entry.getKey());assertThat(result.profileLinked()).isFalse();
            assertThat(result.principalId()).isEqualTo(7L);assertThat(result.expiresAt()).isEqualTo(NOW.plusMinutes(15));
        }
        f.userId=11L;assertThat(f.useCase(1).status("raw").profileLinked()).isTrue();
    }
    @Test void rejectsMissingUnknownAndExpiredHandleIncludingExactDeadline() {
        Fake f=new Fake();
        for(String raw:new String[]{null," "}) {
            assertThatThrownBy(() -> f.useCase(1).status(raw)).hasMessage("Registration handle is required");
        }
        assertThat(f.lookups).isZero();
        f.exists=false;
        assertThatThrownBy(() -> f.useCase(1).status("unknown")).isInstanceOf(AuthResourceMissing.class)
                .hasMessage("Registration not found with handle: not found");
        f.exists=true;
        for (LocalDateTime expiry : List.of(NOW.minusSeconds(1),NOW)) {
            f.expiry=expiry;
            assertThatThrownBy(() -> f.useCase(1).status("raw")).hasMessage("Registration handle expired");
        }
    }
    @Test void cleanupPreservesConfiguredRetentionAndClampsNegativeDaysAtZero() {
        Fake f=new Fake();f.useCase(2).cleanupExpiredHandles();assertThat(f.cutoff).isEqualTo(NOW.minusDays(2));
        f.useCase(-1).cleanupExpiredHandles();assertThat(f.cutoff).isEqualTo(NOW);
    }
    static final class Fake implements RegistrationRecoveryPort {
        boolean exists=true;int lookups;Long userId;
        AuthAccount.LifecycleStatus status=AuthAccount.LifecycleStatus.PENDING_PROFILE;
        LocalDateTime expiry=NOW.plusMinutes(15),cutoff;
        DefaultRegistrationRecoveryUseCase useCase(int retention) { return new DefaultRegistrationRecoveryUseCase(this,retention,CLOCK); }
        public Optional<RegistrationRecoveryFacts> findByHandle(String raw) {
            lookups++;
            return exists ? Optional.of(new RegistrationRecoveryFacts(new AuthAccount(7L,userId,status,1L,"a@example.com","hash",AuthAccount.Role.USER,true,true,null,false,0L,null,null,0,null,false,null,null,0L,null,null,null),expiry)) : Optional.empty();
        }
        public void deleteExpiredBefore(LocalDateTime value) {cutoff=value;}
    }
}
