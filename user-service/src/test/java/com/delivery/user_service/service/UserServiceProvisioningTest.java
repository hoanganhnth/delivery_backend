package com.delivery.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import com.delivery.user_service.service.impl.UserServiceImpl;

class UserServiceProvisioningTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserServiceImpl service = new UserServiceImpl(repository);

    @Test
    void repeatedProvisioningByPrincipalIdReturnsTheExistingUser() {
        User existing = provisionedUser(7L, 42L, "user@example.com", "USER");
        UserRequest request = request(42L, "USER@example.com", "USER");
        when(repository.findByPrincipalId(42L)).thenReturn(Optional.of(existing));

        var result = service.createUser(request);

        assertThat(result.getId()).isEqualTo(7L);
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedPrincipalIdCannotBeReboundToAnotherIdentity() {
        User existing = provisionedUser(7L, 42L, "user@example.com", "USER");
        when(repository.findByPrincipalId(42L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createUser(request(42L, "attacker@example.com", "USER")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void newProvisioningPersistsTheAuthLink() {
        UserRequest request = request(42L, "user@example.com", "USER");
        when(repository.findByPrincipalId(42L)).thenReturn(Optional.empty());
        when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 7L);
                    return user;
                });

        var result = service.createUser(request);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getAuthId()).isEqualTo(42L);
    }

    @Test
    void newPrincipalIdCannotReuseAnExistingEmail() {
        User existing = provisionedUser(7L, 42L, "user@example.com", "USER");
        when(repository.findByPrincipalId(99L)).thenReturn(Optional.empty());
        when(repository.findByEmailIgnoreCase("USER@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createUser(request(99L, "USER@example.com", "USER")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDivergentLegacyAuthIdAndPrincipalId() {
        UserRequest divergent = UserRequest.builder()
                .authId(42L).principalId(99L).email("user@example.com").role("USER").build();

        assertThatThrownBy(() -> service.createUser(divergent))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        verify(repository, never()).findByPrincipalId(org.mockito.ArgumentMatchers.any());
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedBlockAndUnblockCommandsAreIdempotent() {
        User blocked = provisionedUser(7L, 42L, "user@example.com", "USER");
        blocked.setIsBlocked(true);
        blocked.setIsActive(false);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(blocked));

        service.blockUser(7L, 1L, "retry");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());

        blocked.setIsBlocked(false);
        blocked.setIsActive(true);
        service.unblockUser(7L, 1L);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockAndUnblockUsePessimisticUserRowLock() {
        User user = provisionedUser(7L, 42L, "user@example.com", "USER");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));

        service.blockUser(7L, 1L, "fraud review");

        assertThat(user.getIsBlocked()).isTrue();
        assertThat(user.getIsActive()).isFalse();
        verify(repository).findByIdForUpdate(7L);
        verify(repository).save(user);

        user.setIsBlocked(true);
        user.setIsActive(false);
        service.unblockUser(7L, 1L);

        assertThat(user.getIsBlocked()).isFalse();
        assertThat(user.getIsActive()).isTrue();
        verify(repository, org.mockito.Mockito.times(2)).findByIdForUpdate(7L);
        verify(repository, org.mockito.Mockito.times(2)).save(user);
    }

    private UserRequest request(Long authId, String email, String role) {
        return UserRequest.builder().authId(authId).principalId(authId).email(email).role(role).build();
    }

    private User provisionedUser(Long id, Long authId, String email, String role) {
        User user = User.builder().authId(authId).principalId(authId).email(email).role(role).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
