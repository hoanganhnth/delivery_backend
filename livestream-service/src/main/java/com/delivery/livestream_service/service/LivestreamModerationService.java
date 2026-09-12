package com.delivery.livestream_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.dto.request.ModerateLivestreamRequest;
import com.delivery.livestream_service.dto.response.LivestreamModerationResponse;
import com.delivery.livestream_service.entity.LivestreamModerationAudit;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.repository.LivestreamModerationAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class LivestreamModerationService {
    private final LivestreamService livestreams;
    private final LivestreamProductService products;
    private final LivestreamModerationAuditRepository audits;

    public LivestreamModerationService(LivestreamService livestreams, LivestreamProductService products,
                                      LivestreamModerationAuditRepository audits) {
        this.livestreams = livestreams;
        this.products = products;
        this.audits = audits;
    }

    public static void requireAdmin(AuthenticatedActor actor) {
        if (actor == null || actor.getPrincipalId() == null || !actor.isAdmin()) {
            throw new UnauthorizedLivestreamAccessException("ADMIN role is required for moderation");
        }
    }

    @Transactional
    public LivestreamModerationResponse moderate(UUID id, ModerateLivestreamRequest request, AuthenticatedActor actor) {
        requireAdmin(actor);
        switch (request.action()) {
            case WARN -> livestreams.getLivestreamById(id);
            case FORCE_END -> livestreams.endLivestream(id, actor.getLegacyUserId(), "ADMIN");
            case UNPIN -> products.unpinProduct(id, request.productId(), actor.getLegacyUserId(), true);
        }
        LivestreamModerationAudit audit = audits.saveAndFlush(new LivestreamModerationAudit(
                id, actor.getPrincipalId(), request.action(), request.reason().trim(), request.productId()));
        return new LivestreamModerationResponse(audit.getId(), id, audit.getAction(), audit.getProductId(), audit.getAppliedAt());
    }
}
