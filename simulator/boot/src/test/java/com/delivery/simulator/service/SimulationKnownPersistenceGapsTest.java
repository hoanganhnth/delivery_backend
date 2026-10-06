package com.delivery.simulator.service;

import com.delivery.identity.contracts.SimulationContext;
import com.delivery.simulator.entity.SimulationLedgerEntry;
import com.delivery.simulator.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Characterization of recorded gaps, using stale repository responses, not database concurrency proof. */
class SimulationKnownPersistenceGapsTest {
    @Test void twoStaleLeaseReadsCanProduceTheSameFence() {
        var repository=mock(SimulationActorLeaseRepository.class);
        when(repository.findTopByPrincipalIdOrderByFencingTokenDesc(11L)).thenReturn(Optional.empty());
        var service=new SimulationLeaseService(repository,15);
        var first=service.claim(UUID.randomUUID(),11L);
        var second=service.claim(UUID.randomUUID(),11L);
        assertThat(first.getFencingToken()).isEqualTo(1L);
        assertThat(second.getFencingToken()).isEqualTo(first.getFencingToken());
        assertThat(second.getLeaseId()).isNotEqualTo(first.getLeaseId());
        verify(repository,times(2)).save(any());
    }

    @Test void conflictingDeliveryReplayStillPropagatesRepositoryFailure() throws Exception {
        var mapper=new ObjectMapper();var repository=mock(SimulationLedgerRepository.class);
        var observer=new SimulationLedgerObserver(mapper,repository);
        var context=new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,UUID.randomUUID(),UUID.randomUUID(),1L);
        var event=mapper.createObjectNode().put("eventId",UUID.randomUUID().toString())
                .put("orderId",1).put("deliveryId",2).put("totalPrice","100");
        event.set("simulationContext",mapper.valueToTree(context));
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        observer.observe(event.toString());
        var conflict=new IllegalStateException("unique deliveryId violation");
        doThrow(conflict).when(repository).save(any());
        event.put("eventId",UUID.randomUUID().toString());
        assertThatThrownBy(()->observer.observe(event.toString())).isSameAs(conflict);
        verify(repository,times(2)).findById(any());verify(repository,times(2)).save(any());
    }

    @Test void duplicateEventReceiptSkipsInsertWithoutComparingDeliveryPayload() throws Exception {
        var mapper=new ObjectMapper();var repository=mock(SimulationLedgerRepository.class);
        var context=new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,UUID.randomUUID(),UUID.randomUUID(),1L);
        var event=mapper.createObjectNode().put("eventId",UUID.randomUUID().toString()).put("deliveryId",999);
        event.set("simulationContext",mapper.valueToTree(context));
        when(repository.findById(any())).thenReturn(Optional.of(mock(SimulationLedgerEntry.class)));
        new SimulationLedgerObserver(mapper,repository).observe(event.toString());
        verify(repository,never()).save(any());
    }
}
