package com.delivery.saga_orchestrator_service.controller;

import com.delivery.saga_orchestrator_service.dto.RegisterRequest;
import com.delivery.saga_orchestrator_service.saga.order.OrderSaga;
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
    private UserRegistrationSaga userRegistrationSaga;
    private OrderSaga orderSaga;

    // constructor injection can also be used here
    OrchestratorController(UserRegistrationSaga userRegistrationSaga, OrderSaga orderSaga) {
        this.userRegistrationSaga = userRegistrationSaga;
        this.orderSaga = orderSaga;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        userRegistrationSaga.start(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/create-order")
    public ResponseEntity<Void> createOrder(@RequestBody RegisterRequest request) {
        orderSaga.start();
        return ResponseEntity.ok().build();
    }

}
