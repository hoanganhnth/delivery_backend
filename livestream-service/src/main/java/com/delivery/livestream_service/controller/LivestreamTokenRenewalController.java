package com.delivery.livestream_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.dto.response.RenewLivestreamTokenResponse;
import com.delivery.livestream_service.dto.response.TokenResponse;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.LivestreamHostAuthorization;
import com.delivery.livestream_service.service.LivestreamService;
import com.delivery.livestream_service.service.StreamTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPathConstants.LIVESTREAMS)
@ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
public class LivestreamTokenRenewalController {

    private static final int TOKEN_TTL_SECONDS = com.delivery.livestream.domain.LivestreamPolicy.tokenTtl();

    private final LivestreamService livestreamService;
    private final StreamTokenService streamTokenService;
    private final LivestreamHostAuthorization hostAuthorization;

    public LivestreamTokenRenewalController(LivestreamService livestreamService,
                                            StreamTokenService streamTokenService,
                                            LivestreamHostAuthorization hostAuthorization) {
        this.livestreamService = livestreamService;
        this.streamTokenService = streamTokenService;
        this.hostAuthorization = hostAuthorization;
    }

    @PostMapping("/{id}/token/renew")
    public ResponseEntity<BaseResponse<RenewLivestreamTokenResponse>> renew(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null) {
            throw new UnauthorizedLivestreamAccessException("Yêu cầu đăng nhập");
        }
        LivestreamResponse room = livestreamService.getLivestreamById(id);
        try {
            com.delivery.livestream.domain.LivestreamPolicy.renewalStatus(room.getStatus() == null ? null : room.getStatus().name());
        } catch (com.delivery.livestream.domain.LivestreamPolicy.Rejection failure) {
            throw new InvalidLivestreamStatusException(failure.getMessage());
        }
        TokenRole role = TokenRole.valueOf(com.delivery.livestream.domain.LivestreamPolicy.renewalRole(
                actor.getUserId(), room.getSellerId(), actor.isAdmin(), actor.isShopOwner()));
        boolean isOwningHost = role == TokenRole.HOST;
        if (isOwningHost) {
            hostAuthorization.requireHost(actor, room.getRestaurantId());
        }
        TokenResponse token = streamTokenService.generateToken(
                id, actor.getUserId(), role, TOKEN_TTL_SECONDS);
        RenewLivestreamTokenResponse response = new RenewLivestreamTokenResponse(
                id,
                room.getChannelName(),
                token.getToken(),
                com.delivery.livestream.domain.LivestreamPolicy.uid(actor.getUserId()),
                role.name(),
                token.getExpiresAt());
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Gia hạn token livestream thành công"));
    }
}
