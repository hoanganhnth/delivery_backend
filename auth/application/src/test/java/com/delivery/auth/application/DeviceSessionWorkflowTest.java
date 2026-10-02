package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.*;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DeviceSessionWorkflowTest {
    @Test void queryCanonicalizesEmailAndBoundsResultsAtOneHundred() {
        Fake f = new Fake();
        assertThat(f.useCase().activeSessions(" USER@example.com ")).containsExactly(f.session);
        assertThat(f.email).isEqualTo("user@example.com");
        assertThat(f.limit).isEqualTo(100);
        f.exists = false;
        assertThatThrownBy(() -> f.useCase().activeSessions(null)).isInstanceOf(AuthResourceMissing.class)
                .hasMessage("Account not found with email: null");
        assertThat(f.email).isEmpty();
    }
    @Test void deviceRevocationLocksAndExpiresEveryMatchingFamilyAtomically() {
        Fake f = new Fake(); f.useCase().revokeDevice(" USER@example.com ", " phone ");
        assertThat(f.operations).containsExactly("lock", "revoke", "save", "commit");
        assertThat(f.device).isEqualTo("phone");
        assertThat(f.saved.isActive()).isFalse();
        assertThat(f.saved.expiresAt()).isEqualTo(f.revokedAt);
        assertThat(f.saved.lastLoginAt()).isEqualTo(f.session.lastLoginAt());
        assertThat(f.saved.tokenFamilyId()).isEqualTo("family");
    }
    @Test void missingDeviceAndAccountFailBeforeMutationAndNoSessionsIsIdempotent() {
        Fake f = new Fake();
        for (String device : new String[]{null," "}) {
            assertThatThrownBy(() -> f.useCase().revokeDevice("user@example.com",device)).hasMessage("Device ID must not be empty");
        }
        assertThat(f.email).isNull(); assertThat(f.saved).isNull();
        f.exists = false;
        assertThatThrownBy(() -> f.useCase().revokeDevice("Missing@example.com", "phone"))
                .hasMessage("Account not found with email: Missing@example.com");
        f.exists = true; f.matches = List.of(); f.operations.clear();
        f.useCase().revokeDevice("user@example.com", "phone");
        assertThat(f.operations).containsExactly("lock", "commit");
    }
    static final class Fake implements AuthAccountPort, SessionPort, SessionCredentialRevocationPort, AuthTransactionPort {
        boolean exists = true, open;
        String email, device; int limit;
        Session session = new Session(2L,7L,"phone","Phone",Session.DeviceType.MOBILE,"ip","family",true,LocalDateTime.now(),LocalDateTime.now().plusDays(1),LocalDateTime.now());
        List<Session> matches = List.of(session);
        Session saved; LocalDateTime revokedAt;
        List<String> operations = new ArrayList<>();
        DefaultDeviceSessionUseCase useCase() { return new DefaultDeviceSessionUseCase(this,this,this,this); }
        public <T> T required(java.util.function.Supplier<T> operation) { open=true; try { T r=operation.get();operations.add("commit");return r; } finally { open=false; } }
        public Optional<AuthAccount> findByEmail(String value) { email=value;return exists ? Optional.of(new AuthAccount(7L,11L,AuthAccount.LifecycleStatus.ACTIVE,1L,"user@example.com","hash",AuthAccount.Role.USER,true,false,null,false,0L,null,null,0,null,false,null,null,0L,null,null,null)) : Optional.empty(); }
        public Optional<AuthAccount> findById(Long id) { throw new AssertionError(); }
        public AuthAccount save(AuthAccount account) { throw new AssertionError(); }
        public AuthAccount createOrResume(AuthAccount account, java.util.function.Consumer<AuthAccount> verify) { throw new AssertionError(); }
        public List<Session> findActiveByAccount(Long id,LocalDateTime now,int limit) { assertThat(id).isEqualTo(7L);this.limit=limit;return List.of(session); }
        public List<Session> findByAccountAndDeviceForUpdate(Long id,String device) { assertThat(open).isTrue();this.device=device;operations.add("lock");return matches; }
        public void revokeFamily(Long id,LocalDateTime at) { assertThat(open).isTrue();assertThat(id).isEqualTo(2L);revokedAt=at;operations.add("revoke"); }
        public Session save(Session value) { assertThat(open).isTrue();saved=value;operations.add("save");return value; }
        public int deactivateAllForAccount(Long id,LocalDateTime at) { throw new AssertionError(); }
        public int deactivateForAccountAndDevice(Long id,String device,LocalDateTime at) { throw new AssertionError(); }
    }
}
