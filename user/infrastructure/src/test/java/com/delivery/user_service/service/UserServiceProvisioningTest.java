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

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;

class UserServiceProvisioningTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final com.delivery.user.application.DefaultUserBlockStatusUseCase blocks =
            new com.delivery.user.application.DefaultUserBlockStatusUseCase(new JpaUserBlockStatusAdapter(repository));
    private final IdentityOutboxService outbox = mock(IdentityOutboxService.class);
    private final com.delivery.user.application.DefaultUserProfileUseCase profiles =
            new com.delivery.user.application.DefaultUserProfileUseCase(new JpaUserProfileAdapter(repository, outbox));

    @Test
    void repeatedProvisioningByPrincipalIdReturnsTheExistingUser() {
        User existing = provisionedUser(7L, 42L, "user@example.com", "USER");
        UserRequest request = request(42L, "USER@example.com", "USER");
        when(repository.findByPrincipalId(42L)).thenReturn(Optional.of(existing));

        var result = create(request);

        assertThat(result.id()).isEqualTo(7L);
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedPrincipalIdCannotBeReboundToAnotherIdentity() {
        User existing = provisionedUser(7L, 42L, "user@example.com", "USER");
        when(repository.findByPrincipalId(42L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> create(request(42L, "attacker@example.com", "USER")))
                .isInstanceOf(com.delivery.user.domain.ProvisioningIdentityConflict.class);
    }

    @Test
    void newProvisioningPersistsTheAuthLink() {
        UserRequest request = request(42L, "user@example.com", "USER");
        when(repository.findByPrincipalId(42L)).thenReturn(Optional.empty(), Optional.of(
                provisionedUser(7L, 42L, "user@example.com", "USER")));
        when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(repository.insertProvisionedUserIfAbsent(
                42L, 42L, "user@example.com", "USER", null, null, null, null, null))
                .thenReturn(1);

        var result = create(request);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.authId()).isEqualTo(42L);
        verify(repository).insertProvisionedUserIfAbsent(
                42L, 42L, "user@example.com", "USER", null, null, null, null, null);
        verify(outbox).profileCreated(42L, 7L);
    }

    @Test
    void concurrentDuplicateProvisioningReturnsTheDatabaseWinner() {
        UserRequest request = request(42L, "user@example.com", "USER");
        User winner = provisionedUser(7L, 42L, "user@example.com", "USER");
        when(repository.findByPrincipalId(42L))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(repository.insertProvisionedUserIfAbsent(
                42L, 42L, "user@example.com", "USER", null, null, null, null, null))
                .thenReturn(0);

        var result = create(request);

        assertThat(result.id()).isEqualTo(7L);
        verify(repository).insertProvisionedUserIfAbsent(
                42L, 42L, "user@example.com", "USER", null, null, null, null, null);
    }

    @Test
    void newPrincipalIdCannotReuseAnExistingEmail() {
        User existing = provisionedUser(7L, 42L, "user@example.com", "USER");
        when(repository.findByPrincipalId(99L)).thenReturn(Optional.empty());
        when(repository.findByEmailIgnoreCase("USER@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> create(request(99L, "USER@example.com", "USER")))
                .isInstanceOf(com.delivery.user.domain.ProvisioningIdentityConflict.class);
        verify(repository, never()).insertProvisionedUserIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDivergentLegacyAuthIdAndPrincipalId() {
        UserRequest divergent = UserRequest.builder()
                .authId(42L).principalId(99L).email("user@example.com").role("USER").build();

        assertThatThrownBy(() -> create(divergent))
                .isInstanceOf(com.delivery.user.domain.InvalidProvisioningIdentity.class);
        verify(repository, never()).findByPrincipalId(org.mockito.ArgumentMatchers.any());
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedBlockAndUnblockCommandsAreIdempotent() {
        User blocked = provisionedUser(7L, 42L, "user@example.com", "USER");
        blocked.setIsBlocked(true);
        blocked.setIsActive(false);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(blocked));

        blocks.update(new com.delivery.user.application.api.UpdateUserBlockStatusCommand(7L, 1L, true, "retry"));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());

        blocked.setIsBlocked(false);
        blocked.setIsActive(true);
        blocks.update(new com.delivery.user.application.api.UpdateUserBlockStatusCommand(7L, 1L, false, null));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockAndUnblockUsePessimisticUserRowLock() {
        User user = provisionedUser(7L, 42L, "user@example.com", "USER");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));

        blocks.update(new com.delivery.user.application.api.UpdateUserBlockStatusCommand(7L, 1L, true, "fraud review"));

        assertThat(user.getIsBlocked()).isTrue();
        assertThat(user.getIsActive()).isFalse();
        verify(repository).findByIdForUpdate(7L);
        verify(repository).save(user);

        user.setIsBlocked(true);
        user.setIsActive(false);
        blocks.update(new com.delivery.user.application.api.UpdateUserBlockStatusCommand(7L, 1L, false, null));

        assertThat(user.getIsBlocked()).isFalse();
        assertThat(user.getIsActive()).isTrue();
        verify(repository, org.mockito.Mockito.times(2)).findByIdForUpdate(7L);
        verify(repository, org.mockito.Mockito.times(2)).save(user);
    }

    @Test
    void concurrentWinnerCannotRebindIdentityOrPublishOutbox() {
        when(repository.findByPrincipalId(42L)).thenReturn(Optional.empty(), Optional.of(
                provisionedUser(7L, 42L, "attacker@example.com", "USER")));
        assertThatThrownBy(() -> create(request(42L, "user@example.com", "USER")))
                .isInstanceOf(com.delivery.user.domain.ProvisioningIdentityConflict.class);
        org.mockito.Mockito.verifyNoInteractions(outbox);
    }

    private com.delivery.user.application.api.UserProfileResult create(UserRequest request) {
        return profiles.create(new com.delivery.user.application.api.CreateUserCommand(
                request.getAuthId(), request.getPrincipalId(), request.getEmail(), request.getRole(),
                request.getFullName(), request.getPhone(), request.getDob(), request.getAvatarUrl(), request.getAddress()));
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
