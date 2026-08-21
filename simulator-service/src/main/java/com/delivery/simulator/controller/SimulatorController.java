package com.delivery.simulator.controller;

import com.delivery.simulator.config.SimulatorProperties;
import com.delivery.simulator.service.GatewayClient;
import com.delivery.simulator.service.SimulationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SimulationService simulationService;
    private final SimulatorProperties properties;

    public SimulatorController(SimulationService simulationService, SimulatorProperties properties) {
        this.simulationService = simulationService;
        this.properties = properties;
    }

    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                                        @RequestBody JsonNode scenario) {
        authorize(token);
        return simulationService.validate(scenario);
    }

    @PostMapping("/runs")
    public ResponseEntity<Map<String, Object>> start(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                                                     @RequestBody JsonNode scenario) {
        authorize(token);
        return ResponseEntity.accepted().body(simulationService.start(scenario));
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> get(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                                   @PathVariable String runId) {
        authorize(token);
        return simulationService.snapshot(runId);
    }

    @GetMapping("/runs/{runId}/algorithm-traces")
    public Object traces(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                         @PathVariable String runId) {
        authorize(token);
        return simulationService.snapshot(runId).get("algorithmTraces");
    }

    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                             @PathVariable String runId) {
        // Keep the runner token in a request header. Query-string tokens are
        // easily copied into browser history, proxy logs, and referrer data.
        authorize(token);
        return simulationService.stream(runId);
    }

    @PostMapping("/runs/{runId}/pause")
    public Map<String, Object> pause(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                                     @PathVariable String runId) {
        authorize(token);
        return simulationService.pause(runId);
    }

    @PostMapping("/runs/{runId}/resume")
    public Map<String, Object> resume(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                                      @PathVariable String runId) {
        authorize(token);
        return simulationService.resume(runId);
    }

    @PostMapping("/runs/{runId}/abort")
    public Map<String, Object> abort(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                                     @PathVariable String runId) {
        authorize(token);
        return simulationService.abort(runId);
    }

    @DeleteMapping("/runs/{runId}")
    public Map<String, Object> cleanup(@RequestHeader(value = "X-Simulator-Token", required = false) String token,
                                       @PathVariable String runId) {
        authorize(token);
        return simulationService.cleanup(runId);
    }

    private void authorize(String token) {
        if (!properties.isEnabled()) {
            throw new SimulatorDisabledException();
        }
        String expected = properties.getApiToken();
        if (expected != null && !expected.isBlank() && !expected.equals(token)) {
            throw new SimulatorUnauthorizedException();
        }
    }

    @ExceptionHandler(SimulatorDisabledException.class)
    public ResponseEntity<Map<String, Object>> disabled() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("SIMULATOR_DISABLED", "Simulator chỉ tồn tại trong môi trường test/dev"));
    }

    @ExceptionHandler(SimulatorUnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("SIMULATOR_UNAUTHORIZED", "Thiếu hoặc sai X-Simulator-Token"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(error("SIMULATOR_INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("SIMULATOR_NOT_READY", exception.getMessage()));
    }

    @ExceptionHandler(GatewayClient.GatewayException.class)
    public ResponseEntity<Map<String, Object>> gatewayFailure(GatewayClient.GatewayException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error("GATEWAY_CALL_FAILED",
                exception.getOperation() + ": " + exception.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", code);
        response.put("message", message == null ? code : message);
        return response;
    }

    private static final class SimulatorDisabledException extends RuntimeException {
    }

    private static final class SimulatorUnauthorizedException extends RuntimeException {
    }
}
