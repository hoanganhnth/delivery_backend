package com.delivery.livestream_service.service;

import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.StreamProvider;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lifecycle_transaction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"
})
class LivestreamLifecycleTransactionTest {
    @Autowired LivestreamService service;
    @Autowired LivestreamRepository rooms;
    @MockitoBean StreamTokenService tokens;

    @Test
    void failedHostTokenRollsBackStart() {
        Livestream room = room(LivestreamStatus.CREATED);
        when(tokens.generateToken(room.getId(), 7L, TokenRole.HOST, 3600))
                .thenThrow(new IllegalStateException("token offline"));
        assertThatThrownBy(() -> service.startLivestream(room.getId(), 7L, "SHOP_OWNER"))
                .hasMessage("token offline");
        Livestream restored = rooms.findById(room.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(LivestreamStatus.CREATED);
        assertThat(restored.getStartedAt()).isNull();
    }

    @Test
    void explicitCountedJoinRollsBackButDefaultOverloadRetainsSelfInvocationDefect() {
        Livestream room = room(LivestreamStatus.LIVE);
        when(tokens.generateToken(room.getId(), 8L, TokenRole.VIEWER, 3600))
                .thenThrow(new IllegalStateException("token offline"));
        assertThatThrownBy(() -> service.joinLivestream(room.getId(), 8L, true)).hasMessage("token offline");
        assertThat(rooms.findById(room.getId()).orElseThrow().getViewCount()).isZero();
        // The legacy overload invokes the transactional method on this, bypassing
        // the Spring proxy. Keep this limitation visible during equivalence work.
        assertThatThrownBy(() -> service.joinLivestream(room.getId(), 8L)).hasMessage("token offline");
        assertThat(rooms.findById(room.getId()).orElseThrow().getViewCount()).isEqualTo(1L);
    }

    private Livestream room(LivestreamStatus status) {
        Livestream room = new Livestream();
        room.setSellerId(7L);
        room.setRestaurantId(42L);
        room.setTitle("Transaction proof");
        room.setStreamProvider(StreamProvider.AGORA);
        room.setStatus(status);
        room.setChannelName(UUID.randomUUID().toString());
        return rooms.saveAndFlush(room);
    }
}
