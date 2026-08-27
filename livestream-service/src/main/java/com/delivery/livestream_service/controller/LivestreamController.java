package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.dto.request.CreateLivestreamRequest;
import com.delivery.livestream_service.dto.response.JoinLivestreamResponse;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.dto.response.StartLivestreamResponse;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.LivestreamService;
import com.delivery.livestream_service.service.LivestreamHostAuthorization;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPathConstants.LIVESTREAMS)
@ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
public class LivestreamController {

    private final LivestreamService livestreamService;
    private final LivestreamHostAuthorization hostAuthorization;

    public LivestreamController(LivestreamService livestreamService, LivestreamHostAuthorization hostAuthorization) {
        this.livestreamService = livestreamService;
        this.hostAuthorization = hostAuthorization;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<LivestreamResponse>> createLivestream(
            @Valid @RequestBody CreateLivestreamRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        hostAuthorization.requireHost(actor, request.getRestaurantId());
        LivestreamResponse response = livestreamService.createLivestream(request, actor.getUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo livestream thành công"));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<BaseResponse<StartLivestreamResponse>> startLivestream(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        hostAuthorization.requireHost(actor, livestreamService.getLivestreamById(id).getRestaurantId());
        StartLivestreamResponse response = livestreamService.startLivestream(id, actor.getUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, 
                "Bắt đầu livestream thành công. Sử dụng token và channelName để join Agora."));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<BaseResponse<JoinLivestreamResponse>> joinLivestream(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        JoinLivestreamResponse response = livestreamService.joinLivestream(id, actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, response, 
                "Join livestream thành công. Sử dụng token và channelName để xem trên Agora."));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<BaseResponse<LivestreamResponse>> endLivestream(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        hostAuthorization.requireHost(actor, livestreamService.getLivestreamById(id).getRestaurantId());
        LivestreamResponse response = livestreamService.endLivestream(id, actor.getUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Kết thúc livestream thành công"));
    }

    @GetMapping("/active")
    public ResponseEntity<BaseResponse<List<LivestreamResponse>>> getActiveLivestreams() {
        List<LivestreamResponse> response = livestreamService.getActiveLivestreams();
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách livestream đang live thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<LivestreamResponse>> getLivestreamById(@PathVariable UUID id) {
        LivestreamResponse response = livestreamService.getLivestreamById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin livestream thành công"));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<BaseResponse<List<LivestreamResponse>>> getLivestreamsBySeller(
            @PathVariable Long sellerId) {
        List<LivestreamResponse> response = livestreamService.getLivestreamsBySeller(sellerId);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách livestream của seller thành công"));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<List<LivestreamResponse>>> getLivestreamsByRestaurant(
            @PathVariable Long restaurantId, @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        hostAuthorization.requireHost(actor, restaurantId);
        List<LivestreamResponse> response = livestreamService.getLivestreamsByRestaurant(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách livestream của restaurant thành công"));
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null) {
            throw new UnauthorizedLivestreamAccessException("Yêu cầu đăng nhập");
        }
    }

    private String getRoleString(AuthenticatedActor actor) {
        if (actor == null) return null;
        if (actor.isAdmin()) return "ADMIN";
        if (actor.isShopOwner()) return "SHOP_OWNER";
        return "USER";
    }
}
