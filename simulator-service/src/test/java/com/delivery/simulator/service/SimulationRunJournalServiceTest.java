package com.delivery.simulator.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.simulator.repository.SimulationRunJournalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SimulationRunJournalServiceTest {
    @Test
    void persistsOnlyRedactedTimelinePayloadsForTheRun() {
        SimulationRunJournalRepository repository = Mockito.mock(SimulationRunJournalRepository.class);
        SimulationRunJournalService service = new SimulationRunJournalService(repository, new ObjectMapper());
        UUID runId = UUID.randomUUID();

        service.record(runId, Map.of("source", "RUNNER", "title", "Started",
                "payload", Map.of("token", "secret-must-not-persist", "value", "safe")));

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.getRunId().equals(runId) && !entry.getPayloadJson().contains("secret-must-not-persist")
                        && entry.getPayloadJson().contains("safe")));
    }

    @Test
    void readsJournalEntriesForACompletedRun() {
        SimulationRunJournalRepository repository = Mockito.mock(SimulationRunJournalRepository.class);
        SimulationRunJournalService service = new SimulationRunJournalService(repository, new ObjectMapper());
        UUID runId = UUID.randomUUID();
        when(repository.findByRunIdOrderByIdAsc(runId)).thenReturn(List.of(
                new com.delivery.simulator.entity.SimulationRunJournalEntry(runId, java.time.Instant.now(),
                        "ASSERTION", "assertion-1", "{\"status\":\"PASSED\"}")));

        java.util.Map<String, Object> entry = service.entries(runId).get(0);
        assertThat(entry).containsEntry("source", "ASSERTION").containsEntry("title", "assertion-1");
    }
}
