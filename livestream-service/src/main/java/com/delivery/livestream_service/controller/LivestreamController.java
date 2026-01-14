package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.common.constants.HttpHeaderConstants;
import com.delivery.livestream_service.dto.request.CreateLivestreamRequest;
import com.delivery.livestream_service.dto.response.JoinLivestreamResponse;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.dto.response.StartLivestreamResponse;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.LivestreamService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPathConstants.LIVESTREAMS)
public class LivestreamController {

    private final LivestreamService livestreamService;

    public LivestreamController(LivestreamService livestreamService) {
        this.livestreamService = livestreamService;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<LivestreamResponse>> createLivestream(
            @Valid @RequestBody CreateLivestreamRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        LivestreamResponse response = livestreamService.createLivestream(request, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo livestream thành công"));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<BaseResponse<StartLivestreamResponse>> startLivestream(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        StartLivestreamResponse response = livestreamService.startLivestream(id, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, 
                "Bắt đầu livestream thành công. Sử dụng token và channelName để join Agora."));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<BaseResponse<JoinLivestreamResponse>> joinLivestream(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        JoinLivestreamResponse response = livestreamService.joinLivestream(id, userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response, 
                "Join livestream thành công. Sử dụng token và channelName để xem trên Agora."));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<BaseResponse<LivestreamResponse>> endLivestream(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        LivestreamResponse response = livestreamService.endLivestream(id, userId, role);
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
            @PathVariable Long restaurantId) {
        List<LivestreamResponse> response = livestreamService.getLivestreamsByRestaurant(restaurantId);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách livestream của restaurant thành công"));
    }
}
