package com.delivery.saga_orchestrator_service.controller;

import com.delivery.saga_orchestrator_service.dto.RegisterRequest;
import com.delivery.saga_orchestrator_service.saga.user_registration.UserRegistrationSaga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrchestratorController {

    @Autowired
    private UserRegistrationSaga userRegistrationSaga;
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        userRegistrationSaga.start(request);
        return ResponseEntity.ok().build();
    }
}
