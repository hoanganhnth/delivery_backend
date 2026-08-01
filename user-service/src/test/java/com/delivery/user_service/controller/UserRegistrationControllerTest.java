package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.service.UserRegistrationService;
import com.delivery.user_service.service.UserService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationControllerTest {
    private final UserService users = mock(UserService.class);
    private final UserRegistrationService registration = mock(UserRegistrationService.class);
    private final UserController controller = new UserController(users, registration);

    @Test
    void publicRegistrationDelegatesOnlyTheOpaqueHandoff() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setProvisioningToken("opaque-handoff");
        request.setFullName("Customer Test");
        when(registration.register(request)).thenReturn(UserResponse.builder()
                .id(7L).authId(11L).email("user@example.com").role("USER").build());

        var response = controller.registerUser(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().getId()).isEqualTo(7L);
        verify(registration).register(request);
    }
}
