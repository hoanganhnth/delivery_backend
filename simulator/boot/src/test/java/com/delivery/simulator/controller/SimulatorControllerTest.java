package com.delivery.simulator.controller;

import com.delivery.simulator.config.SimulatorProperties;
import com.delivery.simulator.service.GatewayClient;
import com.delivery.simulator.service.SimulationRecoveryService;
import com.delivery.simulator.service.SimulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SimulatorControllerTest {
    private final SimulationService runs = mock(SimulationService.class);
    private final SimulationRecoveryService recovery = mock(SimulationRecoveryService.class);
    private final SimulatorProperties properties = new SimulatorProperties();
    private MockMvc mvc;

    @BeforeEach void setup() {
        properties.setEnabled(true);
        properties.setAdminOnly(false);
        properties.setApiToken("fixture-secret");
        mvc = MockMvcBuilders.standaloneSetup(new SimulatorController(runs, recovery, properties))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test void disabledAndUnauthorizedRequestsCannotReachServices() throws Exception {
        properties.setEnabled(false);
        mvc.perform(get("/api/simulator/runs")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIMULATOR_DISABLED"));
        properties.setEnabled(true);
        mvc.perform(get("/api/simulator/runs")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIMULATOR_UNAUTHORIZED"));
        mvc.perform(get("/api/simulator/runs").header("X-Simulator-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        properties.setAdminOnly(true);
        mvc.perform(get("/api/simulator/runs").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(runs, recovery);
    }

    @Test void validateAndStartReturnServiceResultsAndAcceptedStatus() throws Exception {
        var scenario = new ObjectMapper().readTree("{\"orders\":[]}");
        when(runs.validate(scenario)).thenReturn(Map.of("valid", true));
        when(runs.start(scenario)).thenReturn(Map.of("runId", "r1", "status", "RUNNING"));
        mvc.perform(post("/api/simulator/validate").header("X-Simulator-Token", "fixture-secret")
                .contentType(MediaType.APPLICATION_JSON).content(scenario.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/simulator/runs").header("X-Simulator-Token", "fixture-secret")
                .contentType(MediaType.APPLICATION_JSON).content(scenario.toString()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.runId").value("r1"));
        verify(runs).validate(scenario);
        verify(runs).start(scenario);
    }

    @Test void readsAndControlsPreserveRunIdentityAndResults() throws Exception {
        when(runs.snapshot("r1")).thenReturn(Map.of("runId", "r1", "algorithmTraces", List.of(Map.of("decision", "nearest"))));
        when(runs.listRuns()).thenReturn(List.of(Map.of("runId", "r1")));
        when(runs.journal("r1")).thenReturn(List.of(Map.of("event", "STARTED")));
        when(runs.pause("r1")).thenReturn(Map.of("status", "PAUSED"));
        when(runs.resume("r1")).thenReturn(Map.of("status", "RUNNING"));
        when(runs.abort("r1")).thenReturn(Map.of("status", "ABORTED"));
        when(runs.cleanup("r1")).thenReturn(Map.of("status", "CLEANED"));
        mvc.perform(get("/api/simulator/runs/r1").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runId").value("r1"));
        mvc.perform(get("/api/simulator/runs").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].runId").value("r1"));
        mvc.perform(get("/api/simulator/runs/r1/algorithm-traces").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].decision").value("nearest"));
        mvc.perform(get("/api/simulator/runs/r1/journal").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].event").value("STARTED"));
        for (var control : Map.of("pause", "PAUSED", "resume", "RUNNING", "abort", "ABORTED").entrySet()) {
            mvc.perform(post("/api/simulator/runs/r1/" + control.getKey()).header("X-Simulator-Token", "fixture-secret"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(control.getValue()));
        }
        mvc.perform(delete("/api/simulator/runs/r1").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLEANED"));
        verify(runs).pause("r1"); verify(runs).resume("r1"); verify(runs).abort("r1"); verify(runs).cleanup("r1");
    }

    @Test void serviceErrorsHaveStableHttpStatusAndSafeMessages() throws Exception {
        when(runs.snapshot("invalid")).thenThrow(new IllegalArgumentException("Unknown run"));
        when(runs.snapshot("busy")).thenThrow(new IllegalStateException());
        when(runs.snapshot("gateway")).thenThrow(new GatewayClient.GatewayException(503, "GET delivery", "Gateway returned HTTP 503"));
        mvc.perform(get("/api/simulator/runs/invalid").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATOR_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unknown run"));
        mvc.perform(get("/api/simulator/runs/busy").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("SIMULATOR_NOT_READY"));
        mvc.perform(get("/api/simulator/runs/gateway").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("GATEWAY_CALL_FAILED"))
                .andExpect(jsonPath("$.message").value("GET delivery: Gateway returned HTTP 503"));
        mvc.perform(post("/api/simulator/runs/not-a-uuid/reconcile").header("X-Simulator-Token", "fixture-secret"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recovery);
    }
}
