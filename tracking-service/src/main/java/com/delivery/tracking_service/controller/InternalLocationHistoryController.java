package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.dto.response.LocationHistoryPointResponse;
import com.delivery.tracking_service.service.LocationHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/internal/tracking/location-history")
@Slf4j
public class InternalLocationHistoryController {

    private final LocationHistoryService history;
    private final byte[] internalSecret;

    public InternalLocationHistoryController(
            LocationHistoryService history,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.history = history;
        this.internalSecret = internalSecret.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/deliveries/{deliveryId}")
    public List<LocationHistoryPointResponse> byDelivery(
            @PathVariable long deliveryId,
            @RequestParam(defaultValue = "100") int size,
            @RequestHeader("X-Internal-Auth") String suppliedSecret,
            @RequestHeader("X-Role") String role,
            @RequestHeader("X-User-Id") long supportUserId) {
        requireSupportAccess(suppliedSecret, role, supportUserId);
        List<LocationHistoryPointResponse> points = history.byDelivery(deliveryId, size)
                .stream().map(LocationHistoryPointResponse::from).toList();
        log.info("Location history support read: deliveryId={}, supportUserId={}, pointCount={}",
                deliveryId, supportUserId, points.size());
        return points;
    }

    private void requireSupportAccess(String suppliedSecret, String role, long supportUserId) {
        byte[] supplied = suppliedSecret == null ? new byte[0]
                : suppliedSecret.getBytes(StandardCharsets.UTF_8);
        if (internalSecret.length == 0 || !MessageDigest.isEqual(internalSecret, supplied)
                || !"ADMIN".equals(role) || supportUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support access required");
        }
    }
}
