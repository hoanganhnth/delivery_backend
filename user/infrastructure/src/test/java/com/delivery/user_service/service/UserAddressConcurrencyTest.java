package com.delivery.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Pageable;

import com.delivery.user_service.entity.User;
import com.delivery.user_service.entity.UserAddress;
import com.delivery.user_service.repository.UserAddressRepository;
import com.delivery.user_service.repository.UserRepository;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

class UserAddressConcurrencyTest {

    private final UserAddressRepository addressRepository = mock(UserAddressRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final com.delivery.user.application.DefaultUserAddressUseCase addresses =
            new com.delivery.user.application.DefaultUserAddressUseCase(new JpaUserAddressAdapter(addressRepository, userRepository));

    @Test
    void compatibilityAddressListCapsRepositoryQueryAtOneHundred() {
        when(addressRepository.findByUserIdOrderByCreatedAtDesc(eq(9L), any(Pageable.class)))
                .thenReturn(java.util.List.of());

        assertThat(addresses.byUserId(9L)).isEmpty();

        org.mockito.ArgumentCaptor<Pageable> page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(addressRepository).findByUserIdOrderByCreatedAtDesc(eq(9L), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void settingDefaultSerializesMutationsOnTheOwningUser() {
        UserAddress address = UserAddress.builder().userId(9L).isDefault(false).build();
        ReflectionTestUtils.setField(address, "id", 4L);
        when(addressRepository.findById(4L)).thenReturn(Optional.of(address));
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(User.builder().authId(2L)
                .email("user@example.com").role("USER").build()));
        when(addressRepository.save(address)).thenReturn(address);

        var result = addresses.setDefault(4L);

        assertThat(result.isDefault()).isTrue();
        InOrder order = inOrder(addressRepository, userRepository);
        order.verify(addressRepository).findById(4L);
        order.verify(userRepository).findByIdForUpdate(9L);
        order.verify(addressRepository).findById(4L);
        order.verify(addressRepository).resetDefaultAddressesForUserExcept(9L, 4L);
        order.verify(addressRepository).findById(4L);
        order.verify(addressRepository).save(address);
    }

    @Test
    void serviceLayerRejectsAddressMutationForAnotherProfile() {
        UserAddress address = UserAddress.builder().userId(22L).isDefault(false).build();
        ReflectionTestUtils.setField(address, "id", 4L);
        when(addressRepository.findById(4L)).thenReturn(Optional.of(address));
        User owner = User.builder().authId(11L).principalId(11L).build();
        ReflectionTestUtils.setField(owner, "id", 11L);
        when(userRepository.findByPrincipalId(11L)).thenReturn(Optional.of(owner));
        AuthenticatedActor actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));

        var reads = new com.delivery.user.application.DefaultUserProfileReadUseCase(new JpaUserProfileReadAdapter(userRepository));
        var access = new com.delivery.user.application.DefaultUserAddressAccessUseCase(reads);
        var controller = new com.delivery.user_service.controller.UserAddressController(addresses, access);
        assertThat(controller.deleteAddress(4L, actor).getStatusCode().value()).isEqualTo(403);
        verify(addressRepository, never()).deleteById(4L);
        verify(userRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }
}
