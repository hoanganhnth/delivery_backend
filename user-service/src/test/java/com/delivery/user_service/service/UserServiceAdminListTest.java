package com.delivery.user_service.service;

import com.delivery.user_service.repository.UserRepository;
import com.delivery.user_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceAdminListTest {

    @Test
    void compatibilityListUsesBoundedRepositoryQuery() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findAllByOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(new UserServiceImpl(repository).getAllUsers()).isEmpty();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrderByCreatedAtDesc(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
