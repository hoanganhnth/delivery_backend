package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserBlockStatusRequest;
import com.delivery.user_service.service.UserService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalUserBlockStatusControllerTest {

    private final UserService userService = mock(UserService.class);
    private final InternalUserBlockStatusController controller =
            new InternalUserBlockStatusController(userService, "service-secret");

    @Test
    void synchronizesBlockAndUnblockUsingOnlyTheInternalContract() {
        var blocked = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, true, "fraud review"), "service-secret");
        var unblocked = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, false, null), "service-secret");

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(unblocked.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userService).blockUser(7L, 1L, "fraud review");
        verify(userService).unblockUser(7L, 1L);
    }

    @Test
    void rejectsMissingCredentialOrMissingBlockReason() {
        var forbidden = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, true, "fraud review"), "wrong-secret");
        var invalid = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, true, null), "service-secret");

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        verify(userService, never()).blockUser(7L, 1L, "fraud review");
    }
}
