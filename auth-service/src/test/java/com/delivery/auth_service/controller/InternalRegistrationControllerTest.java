package com.delivery.auth_service.controller;

import com.delivery.auth_service.dto.UserProvisioningIdentityResponse;
import com.delivery.auth_service.service.AccountSecurityService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalRegistrationControllerTest {
    private final AccountSecurityService security = mock(AccountSecurityService.class);
    private final InternalRegistrationController controller =
            new InternalRegistrationController(security, "service-secret");

    @Test
    void resolveFailsClosedWithoutInternalCredential() {
        var request = new com.delivery.auth_service.dto.UserProvisioningTokenRequest();
        request.setProvisioningToken("handoff");

        var response = controller.resolve(request, null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(security, never()).resolveUserProvisioning("handoff");
    }

    @Test
    void resolveAndCompleteUseTheInternalCredential() {
        var resolve = new com.delivery.auth_service.dto.UserProvisioningTokenRequest();
        resolve.setProvisioningToken("handoff");
        when(security.resolveUserProvisioning("handoff"))
                .thenReturn(new UserProvisioningIdentityResponse(
                        11L, "user@example.com", "USER"));

        var resolved = controller.resolve(resolve, "service-secret");

        assertThat(resolved.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resolved.getBody().getData().getAuthId()).isEqualTo(11L);

        var complete = new com.delivery.auth_service.dto.CompleteUserProvisioningRequest();
        complete.setProvisioningToken("handoff");
        complete.setUserId(17L);
        var completed = controller.complete(complete, "service-secret");

        assertThat(completed.getStatusCode().is2xxSuccessful()).isTrue();
        verify(security).completeUserProvisioning("handoff", 17L);
    }
}
