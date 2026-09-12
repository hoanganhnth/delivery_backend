package com.delivery.livestream_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.dto.request.ModerateLivestreamRequest;
import com.delivery.livestream_service.dto.response.LivestreamModerationResponse;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.LivestreamModerationService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping(ApiPathConstants.LIVESTREAMS)
@ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
public class LivestreamModerationController {
    private final LivestreamModerationService moderation;

    public LivestreamModerationController(LivestreamModerationService moderation) {
        this.moderation = moderation;
    }

    @PostMapping("/{id}/moderation")
    public ResponseEntity<BaseResponse<LivestreamModerationResponse>> moderate(
            @PathVariable UUID id, @Valid @RequestBody ModerateLivestreamRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        LivestreamModerationService.requireAdmin(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, moderation.moderate(id, request, actor),
                "Áp dụng kiểm duyệt thành công"));
    }
}
