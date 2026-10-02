package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.domain.ownership.PrincipalOwnershipFacts;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentException;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultRestaurantOwnerAssignmentUseCaseTest {

    @Test
    void shopOwnerCanAssignOnlyItsOwnPrincipalWithoutRemoteLookup() {
        RecordingDirectory directory = new RecordingDirectory(null);
        var useCase = new DefaultRestaurantOwnerAssignmentUseCase(directory);

        assertThat(useCase.resolveOwnerPrincipalId(10L, RestaurantActorRole.SHOP_OWNER, null))
                .isEqualTo(10L);
        assertThat(useCase.resolveOwnerPrincipalId(10L, RestaurantActorRole.SHOP_OWNER, 10L))
                .isEqualTo(10L);
        assertThat(directory.calls).isZero();

        assertViolation(
                () -> useCase.resolveOwnerPrincipalId(10L, RestaurantActorRole.SHOP_OWNER, 11L),
                OwnerAssignmentFailure.CANNOT_ASSIGN_ANOTHER_OWNER);
        assertThat(directory.calls).isZero();
    }

    @Test
    void adminMustSelectAnExistingActiveShopOwner() {
        assertViolation(
                () -> useCase(new RecordingDirectory(null))
                        .resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, null),
                OwnerAssignmentFailure.OWNER_REQUIRED);
        assertViolation(
                () -> useCase(new RecordingDirectory(null))
                        .resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, 42L),
                OwnerAssignmentFailure.OWNER_NOT_FOUND);
        assertViolation(
                () -> useCase(new RecordingDirectory(new PrincipalOwnershipFacts(42L, false, true)))
                        .resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, 42L),
                OwnerAssignmentFailure.OWNER_NOT_SHOP_OWNER);
        assertViolation(
                () -> useCase(new RecordingDirectory(new PrincipalOwnershipFacts(42L, true, false)))
                        .resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, 42L),
                OwnerAssignmentFailure.OWNER_NOT_ACTIVE);

        RecordingDirectory validDirectory =
                new RecordingDirectory(new PrincipalOwnershipFacts(42L, true, true));
        assertThat(useCase(validDirectory)
                .resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, 42L)).isEqualTo(42L);
        assertThat(validDirectory.queriedPrincipalId).isEqualTo(42L);

        assertViolation(
                () -> useCase(new RecordingDirectory(new PrincipalOwnershipFacts(99L, true, true)))
                        .resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, 42L),
                OwnerAssignmentFailure.OWNER_NOT_FOUND);
    }

    @Test
    void unsupportedActorAndInvalidPrincipalIdsFailClosed() {
        var useCase = useCase(new RecordingDirectory(null));
        assertViolation(
                () -> useCase.resolveOwnerPrincipalId(1L, RestaurantActorRole.OTHER, null),
                OwnerAssignmentFailure.ACTOR_NOT_ALLOWED);
        assertThatThrownBy(() -> useCase.resolveOwnerPrincipalId(0L, RestaurantActorRole.ADMIN, 42L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directoryFailureIsNotConvertedToAnAcceptedOwner() {
        PrincipalOwnershipDirectory unavailable = principalId -> {
            throw new IllegalStateException("auth unavailable");
        };

        assertThatThrownBy(() -> useCase(unavailable)
                .resolveOwnerPrincipalId(1L, RestaurantActorRole.ADMIN, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("auth unavailable");
    }

    private DefaultRestaurantOwnerAssignmentUseCase useCase(PrincipalOwnershipDirectory directory) {
        return new DefaultRestaurantOwnerAssignmentUseCase(directory);
    }

    private void assertViolation(Runnable action, OwnerAssignmentFailure expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(OwnerAssignmentException.class)
                .satisfies(error -> assertThat(((OwnerAssignmentException) error).failure())
                        .isEqualTo(expected));
    }

    private static final class RecordingDirectory implements PrincipalOwnershipDirectory {
        private final PrincipalOwnershipFacts result;
        private int calls;
        private long queriedPrincipalId;

        private RecordingDirectory(PrincipalOwnershipFacts result) {
            this.result = result;
        }

        @Override
        public Optional<PrincipalOwnershipFacts> findByPrincipalId(long principalId) {
            calls++;
            queriedPrincipalId = principalId;
            return Optional.ofNullable(result);
        }
    }
}
