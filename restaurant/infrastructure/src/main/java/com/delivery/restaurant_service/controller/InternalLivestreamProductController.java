package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.repository.MenuItemRepository;
import com.delivery.restaurant_service.payload.BaseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Internal-only canonical menu lookup; never exposed through public Gateway. */
@RestController
@RequestMapping("/api/restaurants/internal")
public class InternalLivestreamProductController {
    private final MenuItemRepository menu;
    private final String secret;

    public InternalLivestreamProductController(MenuItemRepository menu,
            @Value("${app.internal.secret:}") String secret) {
        this.menu = menu;
        this.secret = secret;
    }

    public record Product(Long productId, Long restaurantId, String productName,
                          String productImage, String restaurantName) {}

    @GetMapping("/{restaurantId}/livestream-products/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<BaseResponse<Product>> get(@PathVariable Long restaurantId,
            @PathVariable Long productId,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        if (secret == null || secret.isBlank() || token == null ||
                !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(403).body(new BaseResponse<>(0, null, "Forbidden"));
        }
        if (restaurantId == null || restaurantId <= 0 || productId == null || productId <= 0) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(0, null, "Invalid product scope"));
        }
        var item = menu.findById(productId).orElse(null);
        if (item == null || item.getRestaurant() == null ||
                !restaurantId.equals(item.getRestaurant().getId()) || item.getStatus() != MenuItem.Status.AVAILABLE) {
            return ResponseEntity.status(404).body(new BaseResponse<>(0, null, "Available product not found"));
        }
        return ResponseEntity.ok(new BaseResponse<>(1, new Product(item.getId(), restaurantId,
                item.getName(), item.getImage(), item.getRestaurant().getName())));
    }
}
