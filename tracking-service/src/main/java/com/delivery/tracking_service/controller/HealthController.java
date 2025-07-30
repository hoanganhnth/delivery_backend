package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.payload.BaseResponse;
import com.delivery.tracking_service.service.RedisGeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final RedisGeoService redisGeoService;

    @GetMapping
    public ResponseEntity<BaseResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "tracking-service");
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("redis", redisGeoService.isRedisAvailable() ? "UP" : "DOWN");
        health.put("redis_geo", "enabled"); // ✅ Indicate Redis GEO support
        
        return ResponseEntity.ok(new BaseResponse<>(1, health, "Service is healthy"));
    }
}
