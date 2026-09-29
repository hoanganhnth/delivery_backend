package com.delivery.user_service.service;

import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user.application.api.CreateUserCommand;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserProfileUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationServiceTest {
    private final ProvisioningTokenVerifier verifier = mock(ProvisioningTokenVerifier.class);
    private final UserProfileUseCase userProfileUseCase = mock(UserProfileUseCase.class);
    private final UserRegistrationService service = new UserRegistrationService(verifier, userProfileUseCase);

    @Test
    void derivesImmutableIdentityFromSignedHandoffAndWritesProfileOutbox() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setProvisioningToken("signed-handoff");
        request.setFullName("Customer Test");
        when(verifier.verify("signed-handoff")).thenReturn(identity());
        when(userProfileUseCase.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UserProfileResult(
                        7L, 11L, 11L, "ACTIVE", 0L, "user@example.com", "USER",
                        "Customer Test", null, null, null, null, true, false,
                        null, null, null, null, null));

        UserResponse result = service.register(request);

        ArgumentCaptor<CreateUserCommand> trusted = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(userProfileUseCase).create(trusted.capture());
        assertThat(trusted.getValue().authId()).isEqualTo(11L);
        assertThat(trusted.getValue().principalId()).isEqualTo(11L);
        assertThat(trusted.getValue().email()).isEqualTo("user@example.com");
        assertThat(trusted.getValue().role()).isEqualTo("USER");
        assertThat(trusted.getValue().fullName()).isEqualTo("Customer Test");
        assertThat(result.getId()).isEqualTo(7L);
    }

    private ProvisioningTokenVerifier.ProvisioningIdentity identity() {
        return new ProvisioningTokenVerifier.ProvisioningIdentity(11L, "user@example.com", "USER");
    }
}
