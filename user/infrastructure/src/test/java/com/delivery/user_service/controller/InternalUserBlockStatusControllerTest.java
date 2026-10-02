package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserBlockStatusRequest;
import com.delivery.user.application.api.UpdateUserBlockStatusCommand;
import com.delivery.user.application.api.UserBlockStatusUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalUserBlockStatusControllerTest {

    private final UserBlockStatusUseCase userBlockStatusUseCase = mock(UserBlockStatusUseCase.class);
    private final InternalUserBlockStatusController controller =
            new InternalUserBlockStatusController(userBlockStatusUseCase, "service-secret");

    @Test
    void synchronizesBlockAndUnblockUsingOnlyTheInternalContract() {
        var blocked = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, true, "fraud review"), "service-secret");
        var unblocked = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, false, null), "service-secret");

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(unblocked.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userBlockStatusUseCase).update(
                new UpdateUserBlockStatusCommand(7L, 1L, true, "fraud review"));
        verify(userBlockStatusUseCase).update(
                new UpdateUserBlockStatusCommand(7L, 1L, false, null));
    }

    @Test
    void rejectsMissingCredentialOrMissingBlockReason() {
        var forbidden = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, true, "fraud review"), "wrong-secret");
        var invalid = controller.synchronizeBlockStatus(7L,
                new UserBlockStatusRequest(1L, true, null), "service-secret");

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        verify(userBlockStatusUseCase, never()).update(
                new UpdateUserBlockStatusCommand(7L, 1L, true, "fraud review"));
    }
}
