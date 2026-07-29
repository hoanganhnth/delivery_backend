package com.delivery.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Pageable;

import com.delivery.user_service.entity.User;
import com.delivery.user_service.entity.UserAddress;
import com.delivery.user_service.repository.UserAddressRepository;
import com.delivery.user_service.repository.UserRepository;
import com.delivery.user_service.service.impl.UserAddressServiceImpl;

class UserAddressConcurrencyTest {

    private final UserAddressRepository addressRepository = mock(UserAddressRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAddressServiceImpl service = new UserAddressServiceImpl(addressRepository, userRepository);

    @Test
    void compatibilityAddressListCapsRepositoryQueryAtOneHundred() {
        when(addressRepository.findByUserIdOrderByCreatedAtDesc(eq(9L), any(Pageable.class)))
                .thenReturn(java.util.List.of());

        assertThat(service.getAllAddressesByUser(9L)).isEmpty();

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

        var result = service.setDefaultAddress(4L);

        assertThat(result.getIsDefault()).isTrue();
        InOrder order = inOrder(addressRepository, userRepository);
        order.verify(addressRepository).findById(4L);
        order.verify(userRepository).findByIdForUpdate(9L);
        order.verify(addressRepository).resetDefaultAddressesForUser(9L);
        order.verify(addressRepository).save(address);
    }
}
