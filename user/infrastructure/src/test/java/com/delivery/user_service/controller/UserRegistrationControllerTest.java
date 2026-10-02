package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user.application.api.UserBlockStatusUseCase;
import com.delivery.user.application.api.UserProfileReadUseCase;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserProfileUseCase;
import com.delivery.user.application.api.UserRegistrationUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationControllerTest {
    private final UserProfileUseCase profiles = mock(UserProfileUseCase.class);
    private final UserProfileReadUseCase profileReads = mock(UserProfileReadUseCase.class);
    private final UserBlockStatusUseCase blockStatuses = mock(UserBlockStatusUseCase.class);
    private final UserRegistrationUseCase registration = mock(UserRegistrationUseCase.class);
    private final UserController controller = new UserController(profiles, profileReads, blockStatuses, registration);

    @Test
    void publicRegistrationDelegatesOnlyTheOpaqueHandoff() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setProvisioningToken("opaque-handoff");
        request.setFullName("Customer Test");
        when(registration.register(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UserProfileResult(
                        7L, 11L, 11L, "ACTIVE", 0L, "user@example.com", "USER",
                        "Customer Test", null, null, null, null, true, false,
                        null, null, null, null, null));

        var response = controller.registerUser(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().getId()).isEqualTo(7L);
        verify(registration).register(org.mockito.ArgumentMatchers.any());
    }
}
