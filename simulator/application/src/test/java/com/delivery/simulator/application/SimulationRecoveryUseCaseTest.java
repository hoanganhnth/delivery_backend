package com.delivery.simulator.application;
import com.delivery.simulator.application.api.*;
import com.delivery.simulator.application.api.SimulationPorts.*;
import com.delivery.identity.contracts.SimulationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SimulationRecoveryUseCaseTest {
    final ObjectMapper mapper=new ObjectMapper();
    final RunStore<Run> store=mock(RunStore.class);final Run run=mock(Run.class);
    final RecoveryJournal journal=mock(RecoveryJournal.class);final Gateway gateway=mock(Gateway.class);
    final SimulationActorPoolClient actors=mock(SimulationActorPoolClient.class);final Leases<Lease> leases=mock(Leases.class);
    final SimulationDeliveryRecoveryClient recovery=mock(SimulationDeliveryRecoveryClient.class);
    final UUID id=UUID.randomUUID(),cohort=UUID.randomUUID();
    SimulationRecoveryUseCase<Run> app;
    @BeforeEach void setup() throws Exception {
        when(store.findById(id)).thenReturn(Optional.of(run));when(run.getStatus()).thenReturn("ABORTED");
        when(run.getScenarioJson()).thenReturn("{\"cohortId\":\""+cohort+"\",\"customer\":{\"principalId\":11},\"restaurant\":{\"ownerPrincipalId\":12},\"shippers\":[{\"principalId\":13}]}");
        when(journal.payloads(id)).thenReturn(List.of("old text","null","{\"payload\":[{\"deliveryId\":1},{\"deliveryId\":1},{\"deliveryId\":0},{\"deliveryId\":1.5},{\"deliveryId\":9223372036854775808}]}"));
        when(actors.bind(any(),eq(id),eq(cohort))).thenAnswer(i -> new SimulationActorPoolClient.BoundActor(i.getArgument(0),new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,id,cohort,7L),"token"));
        when(gateway.get(any(),any(),any())).thenReturn(mapper.readTree("{\"data\":{\"status\":\"DELIVERED\"}}"));
        app=new SimulationRecoveryUseCase<>(mapper,store,journal,gateway,actors,leases,recovery);
    }
    @Test void durableIdentitiesPrecedeFallbackAndTerminalProofPrecedesRelease() {
        var result=app.reconcile(id);assertThat(result.reconciled()).isTrue();assertThat(result.deliveryIds()).containsExactly(1L);assertThat(result.releasedActors()).isEqualTo(3);assertThat(result.runId()).isEqualTo(id);assertThat(result.deliveryStatuses()).containsEntry(1L,"DELIVERED");
        var order=inOrder(gateway,actors,leases);order.verify(gateway).get(eq("/api/deliveries/1"),eq("token"),eq("recovery-"+id));order.verify(actors).unbind(11L,id,7L);order.verify(actors).unbind(12L,id,7L);order.verify(actors).unbind(13L,id,7L);order.verify(leases).releaseReconciledRun(id);verifyNoInteractions(recovery);
    }
    @ParameterizedTest @ValueSource(strings={"DELIVERED","CANCELLED","REJECTED","SHIPPER_NOT_FOUND","ASSIGNED","UNKNOWN"})
    void fallbackStatusesDecideReleaseWithoutGatewayPoll(String status) {
        when(journal.payloads(id)).thenReturn(List.of());when(recovery.findByRunId(id)).thenReturn(Arrays.asList(new SimulationDeliveryRecoveryClient.DeliveryStatus(null,1L,"DELIVERED"),new SimulationDeliveryRecoveryClient.DeliveryStatus(-1L,1L,"DELIVERED"),new SimulationDeliveryRecoveryClient.DeliveryStatus(2L,1L,status)));
        var result=app.reconcile(id);boolean terminal=Set.of("DELIVERED","CANCELLED","REJECTED","SHIPPER_NOT_FOUND").contains(status);assertThat(result.reconciled()).isEqualTo(terminal);assertThat(result.deliveryStatuses()).containsEntry(2L,status);verifyNoInteractions(gateway);if(!terminal)verify(actors,never()).unbind(any(),any(),anyLong());
    }
    @Test void absentIdentityDoesNotBindAndOptionalPortsRemainSupported() {
        when(journal.payloads(id)).thenReturn(List.of());assertThat(app.reconcile(id).reconciled()).isFalse();verifyNoInteractions(actors);
        app=new SimulationRecoveryUseCase<>(mapper,store,journal,gateway,actors);assertThat(app.reconcile(id).reconciled()).isFalse();
        app=new SimulationRecoveryUseCase<>(mapper,store,journal,gateway,actors,leases);assertThat(app.reconcile(id).reconciled()).isFalse();
    }
    @Test void rawResponseAndNoLeaseAreSupported() throws Exception {
        app=new SimulationRecoveryUseCase<>(mapper,store,journal,gateway,actors);when(gateway.get(any(),any(),any())).thenReturn(mapper.readTree("{\"status\":\"CANCELLED\"}"));assertThat(app.reconcile(id).reconciled()).isTrue();verifyNoInteractions(leases);
    }
    @ParameterizedTest @ValueSource(strings={"RUNNING","PASSED","PAUSED"})
    void rejectsNonRecoverableStatuses(String status) {when(run.getStatus()).thenReturn(status);assertThatThrownBy(()->app.reconcile(id)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(journal,gateway,actors);}
    @Test void rejectsNullMissingAndMalformedScenario() {
        assertThatThrownBy(()->app.reconcile(null)).isInstanceOf(IllegalArgumentException.class);when(store.findById(id)).thenReturn(Optional.empty());assertThatThrownBy(()->app.reconcile(id)).isInstanceOf(IllegalArgumentException.class);
        when(store.findById(id)).thenReturn(Optional.of(run));when(run.getScenarioJson()).thenReturn("not JSON");assertThatThrownBy(()->app.reconcile(id)).hasMessage("Không thể reconcile simulator run");
    }
    @Test void partialBindingFailureRollsBackWithOriginalFenceAndRetainsFailure() {
        var error=new IllegalStateException("auth unavailable");when(actors.bind(eq(13L),eq(id),eq(cohort))).thenThrow(error);doThrow(new IllegalStateException("unbind down")).when(actors).unbind(any(),any(),anyLong());assertThatThrownBy(()->app.reconcile(id)).isSameAs(error);verify(actors).unbind(11L,id,7L);verify(actors).unbind(12L,id,7L);verifyNoInteractions(gateway,leases);
    }
    @Test void invalidActorAndMissingReturnedCustomerRetainExistingFailures() {
        when(run.getScenarioJson()).thenReturn("{\"cohortId\":\""+cohort+"\",\"customer\":{\"principalId\":0}}");assertThatThrownBy(()->app.reconcile(id)).isInstanceOf(IllegalArgumentException.class);
        when(run.getScenarioJson()).thenReturn("{\"cohortId\":\""+cohort+"\",\"customer\":{\"principalId\":11},\"restaurant\":{\"ownerPrincipalId\":12}}");
        when(actors.bind(any(),any(),any())).thenReturn(new SimulationActorPoolClient.BoundActor(99L,new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,id,cohort,1L),"token"));assertThatThrownBy(()->app.reconcile(id)).hasMessageContaining("Thiếu customer");
    }
    @Test void recoveryPreservesExistingMissingReturnedContextOwnershipCheck() {
        UUID foreignRun=UUID.randomUUID(),foreignCohort=UUID.randomUUID();
        when(actors.bind(any(),eq(id),eq(cohort))).thenAnswer(call -> new SimulationActorPoolClient.BoundActor(call.getArgument(0),
                new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,foreignRun,foreignCohort,9L),"foreign-token"));
        assertThat(app.reconcile(id).reconciled()).isTrue();
        verify(actors).unbind(11L,id,9L);
        // Unlike normal start, recovery does not verify returned run/cohort ownership. Report only.
    }

}
