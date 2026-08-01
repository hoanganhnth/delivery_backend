package com.delivery.user_service.service;

import com.delivery.user_service.dto.AuthProvisioningIdentityResponse;
import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationServiceTest {
    private final AuthRegistrationClient auth = mock(AuthRegistrationClient.class);
    private final UserService users = mock(UserService.class);
    private final UserRegistrationService service = new UserRegistrationService(auth, users);

    @Test
    void derivesImmutableIdentityFromAuthAndCompletesTheLink() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setProvisioningToken("opaque-handoff");
        request.setFullName("Customer Test");
        when(auth.resolve("opaque-handoff")).thenReturn(identity());
        when(users.createUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(UserResponse.builder()
                        .id(7L).authId(11L).email("user@example.com")
                        .role("USER").fullName("Customer Test").build());

        UserResponse result = service.register(request);

        ArgumentCaptor<UserRequest> trusted = ArgumentCaptor.forClass(UserRequest.class);
        verify(users).createUser(trusted.capture());
        assertThat(trusted.getValue().getAuthId()).isEqualTo(11L);
        assertThat(trusted.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(trusted.getValue().getRole()).isEqualTo("USER");
        assertThat(trusted.getValue().getFullName()).isEqualTo("Customer Test");
        assertThat(result.getId()).isEqualTo(7L);
        verify(auth).complete("opaque-handoff", 7L);
    }

    private AuthProvisioningIdentityResponse identity() {
        AuthProvisioningIdentityResponse identity = new AuthProvisioningIdentityResponse();
        identity.setAuthId(11L);
        identity.setEmail("user@example.com");
        identity.setRole("USER");
        return identity;
    }
}
