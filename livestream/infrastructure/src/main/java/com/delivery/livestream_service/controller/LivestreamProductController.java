package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.dto.request.PinProductRequest;
import com.delivery.livestream_service.dto.response.LivestreamProductResponse;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.LivestreamProductService;
import com.delivery.livestream_service.service.LivestreamHostAuthorization;
import com.delivery.livestream_service.service.LivestreamService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
@RequestMapping(ApiPathConstants.LIVESTREAMS)
public class LivestreamProductController {

    private final LivestreamProductService productService;
    private final LivestreamService livestreamService;
    private final LivestreamHostAuthorization hostAuthorization;

    public LivestreamProductController(LivestreamProductService productService, LivestreamService livestreamService,
                                       LivestreamHostAuthorization hostAuthorization) {
        this.productService = productService;
        this.livestreamService = livestreamService;
        this.hostAuthorization = hostAuthorization;
    }

    @PostMapping("/{id}/products/pin")
    public ResponseEntity<BaseResponse<LivestreamProductResponse>> pinProduct(
            @PathVariable UUID id,
            @Valid @RequestBody PinProductRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireOwnRoomForProductWrite(id, actor);
        LivestreamProductResponse response = productService.pinProduct(id, request, actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Pin sản phẩm thành công"));
    }

    @DeleteMapping("/{id}/products/{productId}/pin")
    public ResponseEntity<BaseResponse<Void>> unpinProduct(
            @PathVariable UUID id,
            @PathVariable Long productId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireOwnRoomForProductWrite(id, actor);
        productService.unpinProduct(id, productId, actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Bỏ pin sản phẩm thành công"));
    }

    @DeleteMapping("/{id}/products/{productId}")
    public ResponseEntity<BaseResponse<Void>> removeProduct(
            @PathVariable UUID id,
            @PathVariable Long productId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireOwnRoomForProductWrite(id, actor);
        productService.removeProduct(id, productId, actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa sản phẩm khỏi livestream thành công"));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<BaseResponse<List<LivestreamProductResponse>>> getProductsByLivestream(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        List<LivestreamProductResponse> response = productService.getProductsByLivestream(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách sản phẩm thành công"));
    }

    @GetMapping("/{id}/products/pinned")
    public ResponseEntity<BaseResponse<List<LivestreamProductResponse>>> getPinnedProducts(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        List<LivestreamProductResponse> response = productService.getPinnedProducts(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy sản phẩm đang pin thành công"));
    }

    private void requireOwnRoomForProductWrite(UUID id, AuthenticatedActor actor) {
        LivestreamResponse room = livestreamService.getLivestreamById(id);
        if (!actor.getUserId().equals(room.getSellerId())) {
            throw new UnauthorizedLivestreamAccessException(
                    "Only the owning host may change livestream products; ADMIN moderation requires a reason");
        }
        hostAuthorization.requireHost(actor, room.getRestaurantId());
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null) {
            throw new UnauthorizedLivestreamAccessException("Yêu cầu đăng nhập");
        }
    }
}
