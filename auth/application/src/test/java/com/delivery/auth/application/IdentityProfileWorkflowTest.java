package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.AuthAccount.LifecycleStatus;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdentityProfileWorkflowTest {
    private final UUID eventId=UUID.randomUUID();
    private IdentityProfileUseCase.Event event(Long id, Long profile) {
        return new IdentityProfileUseCase.Event(eventId,"identity.profile.created",id,profile,"USER_PROFILE");
    }
    @Test void linksProfileAndAdvancesOnlyChangedLifecycleWithDurableReceipt() {
        Fake f=new Fake();f.account=AccountLifecycleWorkflowTest.account(1L,null,true,true,LifecycleStatus.PENDING_PROFILE,null,0L,false);
        f.core().profileCreated(event(1L,7L),"hash");
        assertThat(f.account.userId()).isEqualTo(7L);
        assertThat(f.account.lifecycleStatus()).isEqualTo(LifecycleStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(f.account.lifecycleVersion()).isEqualTo(1L);
        assertThat(f.account.simulationActor()).isTrue();
        assertThat(f.events).containsExactly("lock","save","outbox:PROFILE_COMPLETED","receipt");
        assertThat(f.receipt.principalId()).isEqualTo(1L);assertThat(f.receipt.fingerprint()).isEqualTo("hash");
        assertThat(f.receipt.processedAt()).isNotNull();
        f.events.clear();f.core().profileCreated(event(1L,7L),"hash");assertThat(f.events).isEmpty();
    }
    @Test void replaysBlockedSnapshotAfterProjectionCreationEvenWithoutAStatusTransition() {
        Fake f=new Fake();f.account=AccountLifecycleWorkflowTest.account(1L,null,false,true,LifecycleStatus.BLOCKED,4L,0L,false);
        f.core().profileCreated(event(1L,7L),"hash");
        assertThat(f.account.lifecycleVersion()).isEqualTo(4L);
        assertThat(f.events).contains("outbox:PROFILE_COMPLETED");
    }
    @Test void unchangedActiveBindingDoesNotProduceAnotherStatusEvent() {
        Fake f=new Fake();f.account=AccountLifecycleWorkflowTest.account(1L,7L,true,false,LifecycleStatus.ACTIVE,null,0L,false);
        f.core().profileCreated(event(1L,7L),"hash");
        assertThat(f.account.lifecycleVersion()).isNull();assertThat(f.events).doesNotContain("outbox:PROFILE_COMPLETED");
    }
    @Test void rejectsMalformedEventsBeforeLookingUpIdentityOrWritingReceipt() {
        Fake f=new Fake();
        var invalid=List.of(new IdentityProfileUseCase.Event(eventId,"other",1L,7L,"USER_PROFILE"),
                event(null,7L),event(1L,null),event(1L,0L),event(1L,-1L),
                new IdentityProfileUseCase.Event(eventId,"identity.profile.created",1L,7L,"OTHER"));
        for(var e:invalid) assertThatThrownBy(() -> f.core().profileCreated(e,"hash"))
                .hasMessage("Invalid identity.profile.created event");
        assertThat(f.events).isEmpty();assertThat(f.lookups).isZero();
    }
    @Test void rejectsReusedEventIdWithConflictingTypePrincipalOrRawPayload() {
        Fake f=new Fake();
        for(var receipt:List.of(new IdentityInboxPort.Receipt(eventId,"other",1L,"hash",LocalDateTime.now()),
                new IdentityInboxPort.Receipt(eventId,"identity.profile.created",2L,"hash",LocalDateTime.now()),
                new IdentityInboxPort.Receipt(eventId,"identity.profile.created",1L,"other",LocalDateTime.now()))) {
            f.receipt=receipt;
            assertThatThrownBy(() -> f.core().profileCreated(event(1L,7L),"hash"))
                    .hasMessage("Conflicting identity event reuse");
        }
        assertThat(f.events).isEmpty();
    }
    @Test void preservesUnknownPrincipalAndConflictingProfileErrorContracts() {
        Fake f=new Fake();
        assertThatThrownBy(() -> f.core().profileCreated(event(1L,7L),"hash"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown principal in profile event");
        f.account=AccountLifecycleWorkflowTest.account(1L,8L,true,false,LifecycleStatus.ACTIVE,2L,0L,false);
        assertThatThrownBy(() -> f.core().profileCreated(event(1L,7L),"hash"))
                .hasMessage("Principal already linked to a different user profile");
        assertThat(f.account.userId()).isEqualTo(8L);assertThat(f.receipt).isNull();
    }
    private static class Fake implements AuthTransactionPort,AccountLifecyclePort,IdentityInboxPort {
        AuthAccount account;Receipt receipt;List<String> events=new ArrayList<>();int lookups;
        DefaultIdentityProfileUseCase core(){return new DefaultIdentityProfileUseCase(this,this,this);}
        public <T>T required(Supplier<T> action){return action.get();}
        public Optional<Receipt> find(UUID id){lookups++;return Optional.ofNullable(receipt);}
        public void save(Receipt receipt){this.receipt=receipt;events.add("receipt");}
        public Change updateLocked(Long id,UnaryOperator<AuthAccount> action){events.add("lock");if(account==null)throw new AuthResourceMissing("missing");var before=account;account=action.apply(account);events.add("save");return new Change(before,account);}
        public List<AuthAccount> pending(int limit){throw new UnsupportedOperationException();}
        public int clearPending(Long id,Long version,LocalDateTime at){throw new UnsupportedOperationException();}
        public void recordFailure(Long id,Long version,String message,LocalDateTime at){throw new UnsupportedOperationException();}
        public void revokeCredentials(Long id,LocalDateTime at){throw new UnsupportedOperationException();}
        public void statusChanged(AuthAccount account,Long adminId,String code){events.add("outbox:"+code);}
    }
}
