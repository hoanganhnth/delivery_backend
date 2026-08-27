package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.dto.request.GenerateTokenRequest;
import com.delivery.livestream_service.dto.response.TokenResponse;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.StreamTokenService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
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
            @AuthenticationPrincipal AuthenticatedActor actor) {
        throw new UnauthorizedLivestreamAccessException(
                "Caller-controlled livestream token issuance is disabled");
    }
}
