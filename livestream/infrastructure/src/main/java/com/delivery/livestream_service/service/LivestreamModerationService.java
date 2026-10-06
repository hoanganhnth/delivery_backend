package com.delivery.livestream_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.dto.request.ModerateLivestreamRequest;
import com.delivery.livestream_service.dto.response.LivestreamModerationResponse;
import com.delivery.livestream_service.entity.LivestreamModerationAudit;
import com.delivery.livestream_service.repository.LivestreamModerationAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import com.delivery.livestream.api.ModerationPorts;
import com.delivery.livestream.application.ModerationUseCase;
import com.delivery.livestream.domain.LivestreamPolicy;
import com.delivery.livestream_service.enums.LivestreamModerationAction;

@Service
public class LivestreamModerationService {
    private final ModerationUseCase<LivestreamModerationResponse> useCase;
    public LivestreamModerationService(LivestreamService rooms, LivestreamProductService products, LivestreamModerationAuditRepository audits) {
        useCase = new ModerationUseCase<>(new ModerationPorts<>() {
            public void inspect(UUID id) { rooms.getLivestreamById(id); }
            public void end(UUID id, Long user) { rooms.endLivestream(id, user, "ADMIN"); }
            public void unpin(UUID id, Long product, Long user) { products.unpinProduct(id, product, user, true); }
            public LivestreamModerationResponse audit(UUID id, Long principal, String action, String reason, Long product) {
                var audit = audits.saveAndFlush(new LivestreamModerationAudit(id, principal, LivestreamModerationAction.valueOf(action), reason, product));
                return new LivestreamModerationResponse(audit.getId(), id, audit.getAction(), audit.getProductId(), audit.getAppliedAt());
            }
        });
    }
    public static void requireAdmin(AuthenticatedActor actor) {
        LivestreamCompatibility.run(() -> LivestreamPolicy.moderator(actor == null ? null : actor.getPrincipalId(), actor != null && actor.isAdmin()));
    }
    @Transactional
    public LivestreamModerationResponse moderate(UUID id, ModerateLivestreamRequest request, AuthenticatedActor actor) {
        requireAdmin(actor);
        return LivestreamCompatibility.call(() -> useCase.moderate(id, actor.getPrincipalId(), actor.getLegacyUserId(), actor.isAdmin(), request.action().name(), request.reason(), request.productId()));
    }
}
