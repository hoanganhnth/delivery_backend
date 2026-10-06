package com.delivery.livestream.application;

import com.delivery.livestream.api.RestaurantOwnershipPort;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostAuthorizationUseCaseTest {
    private final RestaurantOwnershipPort ownership = mock(RestaurantOwnershipPort.class);
    private final HostAuthorizationUseCase useCase = new HostAuthorizationUseCase(ownership);

    @Test
    void denialAndAdminBypassDoNotCallRestaurant() {
        assertThatThrownBy(() -> useCase.requireHost(null, true, true, 42L, 1L, null))
                .hasMessage("ADMIN or SHOP_OWNER role is required");
        assertThatThrownBy(() -> useCase.requireHost(7L, false, false, 42L, 1L, 7L))
                .hasMessage("ADMIN or SHOP_OWNER role is required");
        useCase.requireHost(7L, true, false, 42L, 1L, 7L);
        useCase.requireHost(7L, true, true, 42L, 1L, 7L);
        verifyNoInteractions(ownership);
    }

    @Test
    void shopOwnerPassesBothIdentitiesAndPropagatesAuthorityFailure() {
        useCase.requireHost(7L, false, true, 42L, 1L, 7L);
        verify(ownership).requireOwnedBy(42L, 1L, 7L);
        doThrow(new IllegalStateException("authority offline")).when(ownership).requireOwnedBy(42L, 1L, 7L);
        assertThatThrownBy(() -> useCase.requireHost(7L, false, true, 42L, 1L, 7L))
                .hasMessage("authority offline");
    }
}
