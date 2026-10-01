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
    void historicalPayloadIsRedactedOnReadWithoutRewritingJournalRows() {
        var repository = Mockito.mock(SimulationRunJournalRepository.class);
        var mapper = new ObjectMapper();
        var service = new SimulationRunJournalService(repository, mapper);
        UUID runId = UUID.randomUUID();
        var row = new com.delivery.simulator.entity.SimulationRunJournalEntry(runId, java.time.Instant.now(),
                "RUNNER", "legacy", "{\"nested\":[{\"accessToken\":\"credential-marker\",\"seed\":42}]}");
        when(repository.findByRunIdOrderByIdAsc(runId)).thenReturn(List.of(row));

        assertThat(service.entries(runId).get(0).get("payload").toString())
                .doesNotContain("credential-marker").contains("42");
        assertThat(row.getPayloadJson()).contains("credential-marker");
        verify(repository).findByRunIdOrderByIdAsc(runId);
        Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void malformedStoredPayloadDoesNotHideLaterJournalEntriesOrExposeParserDetails() {
        var repository = Mockito.mock(SimulationRunJournalRepository.class);
        var service = new SimulationRunJournalService(repository, new ObjectMapper());
        UUID runId = UUID.randomUUID();
        var recordedAt = java.time.Instant.now();
        when(repository.findByRunIdOrderByIdAsc(runId)).thenReturn(List.of(
                new com.delivery.simulator.entity.SimulationRunJournalEntry(runId, recordedAt,
                        "RUNNER", "bad", "invalid-json-marker"),
                new com.delivery.simulator.entity.SimulationRunJournalEntry(runId, recordedAt,
                        "ASSERTION", "good", "{\"status\":\"PASSED\"}")));
        var result = service.entries(runId);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("recordedAt", recordedAt)
                .containsEntry("payload", Map.of("journalError", "invalid payload"));
        assertThat(result.get(1).get("payload").toString()).contains("PASSED");
        assertThat(result.toString()).doesNotContain("invalid-json-marker");
    }

    @Test
    void nullInputsDoNotAccessJournalStorage() {
        var repository = Mockito.mock(SimulationRunJournalRepository.class);
        var service = new SimulationRunJournalService(repository, new ObjectMapper());
        service.record(null, Map.of());
        service.record(UUID.randomUUID(), null);
        assertThat(service.entries(null)).isEmpty();
        Mockito.verifyNoInteractions(repository);
    }

    @Test
    void missingMetadataUsesDefaultsAndRecordingPreservesCallerCredentials() {
        var repository = Mockito.mock(SimulationRunJournalRepository.class);
        var service = new SimulationRunJournalService(repository, new ObjectMapper());
        var nested = new java.util.HashMap<String, Object>();
        nested.put("ownerToken", "credential-marker");
        nested.put("safe", 42);
        var input = Map.<String, Object>of("payload", List.of(nested));
        service.record(UUID.randomUUID(), input);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.getSource().equals("RUNNER") && entry.getTitle().equals("event")
                        && !entry.getPayloadJson().contains("credential-marker")
                        && entry.getPayloadJson().contains("42")));
        assertThat(nested).containsEntry("ownerToken", "credential-marker");
    }

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
