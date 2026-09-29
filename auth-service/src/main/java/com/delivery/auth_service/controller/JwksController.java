package com.delivery.auth_service.controller;

import com.delivery.auth_service.application.port.in.JwksUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final JwksUseCase jwksUseCase;

    public JwksController(JwksUseCase jwksUseCase) {
        this.jwksUseCase = jwksUseCase;
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=300")
                .body(jwksUseCase.getJwks());
    }
}
