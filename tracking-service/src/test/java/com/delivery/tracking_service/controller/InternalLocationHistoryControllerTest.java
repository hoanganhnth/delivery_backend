package com.delivery.tracking_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.tracking_service.service.LocationHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalLocationHistoryControllerTest {

    private final LocationHistoryService history = mock(LocationHistoryService.class);
    private final InternalLocationHistoryController controller =
            new InternalLocationHistoryController(history, "internal-test-secret");

    @Test
    void rejectsClientOrNonAdminAccessBeforeHistoryQuery() {
        AuthenticatedActor shipperActor = new AuthenticatedActor(7L, "shipper@example.com", Set.of("SHIPPER"));
        AuthenticatedActor adminActor = new AuthenticatedActor(7L, "admin@example.com", Set.of("ADMIN"));

        assertThatThrownBy(() -> controller.byDelivery(
                100L, 100, "internal-test-secret", null, shipperActor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        assertThatThrownBy(() -> controller.byDelivery(
                100L, 100, "wrong", null, adminActor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(history);
    }
}
