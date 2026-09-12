package com.delivery.livestream_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.dto.request.ModerateLivestreamRequest;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamModerationAction;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.StreamProvider;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.repository.LivestreamModerationAuditRepository;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:moderation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class LivestreamModerationIntegrationTest {
    @Autowired LivestreamModerationService moderation;
    @Autowired LivestreamRepository rooms;
    @Autowired LivestreamProductRepository products;
    @Autowired LivestreamModerationAuditRepository audits;
    @Autowired LivestreamProductService productService;
    @Autowired JdbcTemplate jdbc;
    private final AuthenticatedActor admin = new AuthenticatedActor(900L, 9L, "admin@example.test", Set.of("ADMIN"));

    @Test
    void warningRecordsServerPrincipalAndTimestampWithoutChangingRoom() {
        Livestream room = room(LivestreamStatus.LIVE);
        var response = moderation.moderate(room.getId(), request(LivestreamModerationAction.WARN, null), admin);
        var audit = audits.findById(response.auditId()).orElseThrow();
        assertThat(audit.getActorPrincipalId()).isEqualTo(900L);
        assertThat(audit.getLivestreamId()).isEqualTo(room.getId());
        assertThat(audit.getReason()).isEqualTo("Policy violation");
        assertThat(audit.getAction()).isEqualTo(LivestreamModerationAction.WARN);
        assertThat(audit.getAppliedAt()).isEqualTo(response.appliedAt());
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(LivestreamStatus.LIVE);
    }

    @Test
    void forceEndCommitsEndedRoomAndAudit() {
        Livestream room = room(LivestreamStatus.LIVE);
        var response = moderation.moderate(room.getId(), request(LivestreamModerationAction.FORCE_END, null), admin);
        Livestream ended = rooms.findById(room.getId()).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(LivestreamStatus.ENDED);
        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(audits.findById(response.auditId()).orElseThrow().getAction()).isEqualTo(LivestreamModerationAction.FORCE_END);
    }

    @Test
    void unpinCommitsTargetChangeAndAuditButNonOwnerStillCannotUnpin() {
        Livestream room = room(LivestreamStatus.LIVE);
        LivestreamProduct product = new LivestreamProduct();
        product.setLivestreamId(room.getId()); product.setProductId(23L); product.setIsPinned(true);
        product = products.saveAndFlush(product);
        assertThatThrownBy(() -> productService.unpinProduct(room.getId(), 23L, 99L))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        var response = moderation.moderate(room.getId(), request(LivestreamModerationAction.UNPIN, 23L), admin);
        assertThat(products.findById(product.getId()).orElseThrow().getIsPinned()).isFalse();
        assertThat(audits.findById(response.auditId()).orElseThrow().getProductId()).isEqualTo(23L);
    }

    @Test
    void nonAdminAndMissingPrincipalCannotCreateAudit() {
        long before = audits.count();
        for (AuthenticatedActor actor : new AuthenticatedActor[]{null,
                new AuthenticatedActor(1L, 1L, "host@example.test", Set.of("SHOP_OWNER")),
                new AuthenticatedActor(null, 9L, "bad@example.test", Set.of("ADMIN"))}) {
            assertThatThrownBy(() -> moderation.moderate(UUID.randomUUID(), request(LivestreamModerationAction.WARN, null), actor))
                    .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        }
        assertThat(audits.count()).isEqualTo(before);
    }

    @Test
    void missingRoomAndInvalidTransitionDoNotCreateAudit() {
        long before = audits.count();
        assertThatThrownBy(() -> moderation.moderate(UUID.randomUUID(), request(LivestreamModerationAction.WARN, null), admin))
                .isInstanceOf(LivestreamNotFoundException.class);
        Livestream ended = room(LivestreamStatus.ENDED);
        assertThatThrownBy(() -> moderation.moderate(ended.getId(), request(LivestreamModerationAction.FORCE_END, null), admin))
                .isInstanceOf(InvalidLivestreamStatusException.class);
        assertThat(audits.count()).isEqualTo(before);
    }

    @Test
    void failedAuditInsertRollsBackForceEnd() {
        Livestream room = room(LivestreamStatus.LIVE);
        long before = audits.count();
        jdbc.execute("ALTER TABLE livestream_moderation_audits ADD CONSTRAINT test_reject_moderation CHECK (livestream_id <> '" + room.getId() + "')");
        try {
            assertThatThrownBy(() -> moderation.moderate(room.getId(), request(LivestreamModerationAction.FORCE_END, null), admin))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("ALTER TABLE livestream_moderation_audits DROP CONSTRAINT test_reject_moderation");
        }
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(LivestreamStatus.LIVE);
        assertThat(audits.count()).isEqualTo(before);
    }

    private ModerateLivestreamRequest request(LivestreamModerationAction action, Long productId) {
        return new ModerateLivestreamRequest(action, "Policy violation", productId);
    }

    private Livestream room(LivestreamStatus status) {
        Livestream room = new Livestream();
        room.setSellerId(1L); room.setRestaurantId(2L); room.setTitle("Test room");
        room.setStreamProvider(StreamProvider.AGORA); room.setStatus(status);
        room.setChannelName(UUID.randomUUID().toString()); room.setStartedAt(LocalDateTime.now().minusMinutes(2));
        return rooms.saveAndFlush(room);
    }
}
