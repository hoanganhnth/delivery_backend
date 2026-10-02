package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SimulationBindingWorkflowTest {
    private static final UUID RUN=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COHORT=UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Test void claimAndReleasePreserveIdentityFactsAndAdvanceFence() {
        Fake f=new Fake();var core=f.core();
        var one=core.bind(9L,RUN,COHORT);
        assertThat(one.bindingVersion()).isEqualTo(1L);
        assertThat(f.account.activeSimulationRunId()).isEqualTo(RUN);
        assertThat(f.account.simulationCohortId()).isEqualTo(COHORT);
        assertThat(f.account.passwordHash()).isEqualTo("hash");
        assertThat(core.bind(9L,RUN,COHORT).bindingVersion()).isEqualTo(2L);
        core.unbind(9L,RUN,2L);
        assertThat(f.account.activeSimulationRunId()).isNull();
        assertThat(f.account.simulationBindingVersion()).isEqualTo(3L);
        assertThatThrownBy(()->core.unbind(9L,RUN,2L)).hasMessage("Simulation binding fence does not match");
        assertThat(core.bind(9L,RUN,COHORT).bindingVersion()).isEqualTo(4L);
        assertThat(f.events).containsSubsequence("begin","lock","save","commit");
    }
    @Test void deniesLeasedOrOtherCohortActorsWithoutChangingFence() {
        Fake f=new Fake();var core=f.core();core.bind(9L,RUN,COHORT);
        assertThatThrownBy(()->core.bind(9L,UUID.randomUUID(),COHORT)).hasMessageContaining("another simulation run");
        assertThatThrownBy(()->core.bind(9L,RUN,UUID.randomUUID())).hasMessageContaining("another cohort");
        assertThat(f.account.simulationBindingVersion()).isEqualTo(1L);
        assertThatThrownBy(()->core.unbind(9L,UUID.randomUUID(),1L)).hasMessage("Simulation binding fence does not match");
        assertThatThrownBy(()->core.unbind(9L,RUN,0L)).hasMessage("principalId, runId and bindingVersion are required");
    }
    @Test void validatesInputsAndApprovalBeforePersistenceMutation() {
        Fake f=new Fake();var core=f.core();
        for(var id:Arrays.asList(null,0L,-1L))
            assertThatThrownBy(()->core.bind(id,RUN,COHORT)).hasMessage("principalId, runId and cohortId are required");
        assertThatThrownBy(()->core.bind(9L,null,COHORT)).hasMessage("principalId, runId and cohortId are required");
        assertThatThrownBy(()->core.bind(9L,RUN,null)).hasMessage("principalId, runId and cohortId are required");
        assertThat(f.events).isEmpty();
        f.account=account(false,101L);
        assertThatThrownBy(()->core.bind(9L,RUN,COHORT)).hasMessage("Account is not approved as a simulation actor");
    }
    @Test void issuesTokenWithCommittedFenceOnlyForProvisionedIdentity() {
        Fake f=new Fake();var bound=f.core().bindAndIssueAccessToken(9L,RUN,COHORT);
        assertThat(bound.accessToken()).isEqualTo("signed");
        assertThat(bound.binding().bindingVersion()).isEqualTo(1L);
        assertThat(f.issued.id()).isEqualTo(9L);
        assertThat(f.issued.userId()).isEqualTo(101L);
        assertThat(f.issued.email()).isEqualTo("virtual@example.test");
        assertThat(f.issuedBinding).isEqualTo(bound.binding());
        f=new Fake();f.account=account(true,null);
        var noProfile=f;
        assertThatThrownBy(()->noProfile.core().bindAndIssueAccessToken(9L,RUN,COHORT))
                .hasMessage("Simulation actor is not a provisioned application identity");
        assertThat(noProfile.issued).isNull();
        f=new Fake();f.token=null;var noToken=f;
        assertThatThrownBy(()->noToken.core().bindAndIssueAccessToken(9L,RUN,COHORT))
                .hasMessage("simulation context and access token are required");
    }
    static AuthAccount account(Boolean approved,Long userId) {
        return new AuthAccount(9L,userId,AuthAccount.LifecycleStatus.ACTIVE,2L,"virtual@example.test","hash",
                AuthAccount.Role.SHIPPER,true,false,LocalDateTime.now(),false,0L,null,null,0,null,approved,null,null,
                null,null,null,null);
    }
    static final class Fake implements AccountLifecyclePort,AuthTransactionPort,SimulationAccessTokenPort {
        AuthAccount account=account(true,101L);AuthAccount issued;SimulationBindingUseCase.Binding issuedBinding;
        String token="signed";List<String> events=new ArrayList<>();
        DefaultSimulationBindingUseCase core(){return new DefaultSimulationBindingUseCase(this,this,this);}
        public <T>T required(Supplier<T> op){events.add("begin");T result=op.get();events.add("commit");return result;}
        public Change updateLocked(Long id,UnaryOperator<AuthAccount> op){events.add("lock");var before=account;account=op.apply(before);events.add("save");return new Change(before,account);}
        public String issue(AuthAccount account,SimulationBindingUseCase.Binding binding){issued=account;issuedBinding=binding;return token;}
        public List<AuthAccount> pending(int limit){throw new UnsupportedOperationException();}
        public int clearPending(Long id,Long version,LocalDateTime at){throw new UnsupportedOperationException();}
        public void recordFailure(Long id,Long version,String error,LocalDateTime at){throw new UnsupportedOperationException();}
        public void revokeCredentials(Long id,LocalDateTime at){throw new UnsupportedOperationException();}
        public void statusChanged(AuthAccount a,Long by,String reason){throw new UnsupportedOperationException();}
    }
}
