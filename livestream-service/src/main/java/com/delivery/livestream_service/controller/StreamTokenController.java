package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.common.constants.HttpHeaderConstants;
import com.delivery.livestream_service.dto.request.GenerateTokenRequest;
import com.delivery.livestream_service.dto.response.TokenResponse;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.StreamTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPathConstants.LIVESTREAMS)
public class StreamTokenController {

    private final StreamTokenService streamTokenService;

    public StreamTokenController(StreamTokenService streamTokenService) {
        this.streamTokenService = streamTokenService;
    }

    @PostMapping("/{id}/token")
    public ResponseEntity<BaseResponse<TokenResponse>> generateToken(
            @PathVariable UUID id,
            @Valid @RequestBody GenerateTokenRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        TokenResponse response = streamTokenService.generateToken(
                id, userId, request.getRole(), request.getExpireSeconds());
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Tạo token thành công"));
    }
}
