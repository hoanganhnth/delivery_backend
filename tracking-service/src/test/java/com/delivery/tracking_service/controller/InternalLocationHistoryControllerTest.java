package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.service.LocationHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalLocationHistoryControllerTest {

    private final LocationHistoryService history = mock(LocationHistoryService.class);
    private final InternalLocationHistoryController controller =
            new InternalLocationHistoryController(history, "internal-test-secret");

    @Test
    void rejectsClientOrNonAdminAccessBeforeHistoryQuery() {
        assertThatThrownBy(() -> controller.byDelivery(
                100L, 100, "internal-test-secret", "SHIPPER", 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> controller.byDelivery(
                100L, 100, "wrong", "ADMIN", 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(history);
    }
}
