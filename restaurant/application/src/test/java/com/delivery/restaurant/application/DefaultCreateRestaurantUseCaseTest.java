package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.CreateRestaurantCommand;
import com.delivery.restaurant.application.api.CreateRestaurantResult;
import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.application.api.RestaurantCreationPort;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentException;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentFailure;
import com.delivery.restaurant.domain.ownership.PrincipalOwnershipFacts;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCreateRestaurantUseCaseTest {
    private final RecordingCreationPort persistence = new RecordingCreationPort();
    private int directoryCalls;

    @Test
    void shopOwnerCreatesForSelfWithoutDirectoryAndReturnsPersistenceResultUnchanged() {
        var useCase = useCase(id -> { throw new AssertionError("SHOP_OWNER must be network-free"); });
        for (Long owner : new Long[] {null, 101L}) {
            var command = command(101L, 7L, RestaurantActorRole.SHOP_OWNER, owner, null, null);
            assertThat(useCase.create(command)).isSameAs(persistence.result);
            assertThat(persistence.command).isSameAs(command);
            assertThat(persistence.ownerPrincipalId).isEqualTo(101L);
            assertThat(persistence.command.creatorId()).isEqualTo(7L);
        }
        assertThat(persistence.calls).isEqualTo(2);
    }

    @Test
    void adminResolvesActiveShopOwnerBeforePersistingWithDistinctCreator() {
        var useCase = useCase(id -> {
            directoryCalls++;
            assertThat(id).isEqualTo(42L);
            assertThat(persistence.calls).isZero();
            return Optional.of(new PrincipalOwnershipFacts(42L, true, true));
        });
        var command = command(1L, 7L, RestaurantActorRole.ADMIN, 42L,
                LocalTime.of(18, 0), LocalTime.of(2, 0));

        assertThat(useCase.create(command)).isSameAs(persistence.result);
        assertThat(directoryCalls).isEqualTo(1);
        assertThat(persistence.calls).isEqualTo(1);
        assertThat(persistence.ownerPrincipalId).isEqualTo(42L);
        assertThat(persistence.command).isSameAs(command);
    }

    @Test
    void missingActorOrCreatorNeverReachesDirectoryOrPersistence() {
        var useCase = useCase(id -> { throw new AssertionError("No directory call expected"); });
        for (CreateRestaurantCommand command : new CreateRestaurantCommand[] {
                command(null, 7L, RestaurantActorRole.SHOP_OWNER, null, null, null),
                command(101L, null, RestaurantActorRole.SHOP_OWNER, null, null, null)}) {
            assertViolation(() -> useCase.create(command), OwnerAssignmentFailure.ACTOR_NOT_ALLOWED);
        }
    }

    @Test
    void unsupportedOrMissingRoleAndForeignSelfAssignmentNeverPersist() {
        var useCase = useCase(id -> { throw new AssertionError("No directory call expected"); });
        assertViolation(() -> useCase.create(command(101L, 7L, RestaurantActorRole.OTHER,
                null, null, null)), OwnerAssignmentFailure.ACTOR_NOT_ALLOWED);
        assertViolation(() -> useCase.create(command(101L, 7L, null,
                null, null, null)), OwnerAssignmentFailure.ACTOR_NOT_ALLOWED);
        assertViolation(() -> useCase.create(command(101L, 7L, RestaurantActorRole.SHOP_OWNER,
                42L, null, null)), OwnerAssignmentFailure.CANNOT_ASSIGN_ANOTHER_OWNER);
    }

    @Test
    void missingOrNonPositiveAdminTargetAndInvalidActorNeverPersist() {
        var useCase = useCase(id -> { throw new AssertionError("No directory call expected"); });
        assertViolation(() -> useCase.create(command(1L, 7L, RestaurantActorRole.ADMIN,
                null, null, null)), OwnerAssignmentFailure.OWNER_REQUIRED);
        for (long invalid : new long[] {0, -1}) {
            assertThatThrownBy(() -> useCase.create(command(1L, 7L, RestaurantActorRole.ADMIN,
                    invalid, null, null))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> useCase.create(command(invalid, 7L, RestaurantActorRole.ADMIN,
                    42L, null, null))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(persistence.calls).isZero();
    }

    @Test
    void absentMismatchedInactiveOrNonOwnerDirectoryResultNeverPersists() {
        assertViolation(() -> useCase(id -> Optional.empty()).create(adminCommand()),
                OwnerAssignmentFailure.OWNER_NOT_FOUND);
        assertViolation(() -> useCase(id -> Optional.of(new PrincipalOwnershipFacts(99L, true, true)))
                .create(adminCommand()), OwnerAssignmentFailure.OWNER_NOT_FOUND);
        assertViolation(() -> useCase(id -> Optional.of(new PrincipalOwnershipFacts(42L, false, true)))
                .create(adminCommand()), OwnerAssignmentFailure.OWNER_NOT_SHOP_OWNER);
        assertViolation(() -> useCase(id -> Optional.of(new PrincipalOwnershipFacts(42L, true, false)))
                .create(adminCommand()), OwnerAssignmentFailure.OWNER_NOT_ACTIVE);
    }

    @Test
    void directoryFailurePropagatesWithoutPersistence() {
        var failure = new IllegalStateException("auth unavailable");
        var useCase = useCase(id -> { throw failure; });
        assertThatThrownBy(() -> useCase.create(adminCommand())).isSameAs(failure);
        assertThat(persistence.calls).isZero();
    }

    @Test
    void eitherIncompleteScheduleFailsBeforePersistence() {
        var useCase = useCase(id -> { throw new AssertionError("No directory call expected"); });
        var hour = LocalTime.of(18, 0);
        for (CreateRestaurantCommand command : new CreateRestaurantCommand[] {
                command(101L, 7L, RestaurantActorRole.SHOP_OWNER, null, hour, null),
                command(101L, 7L, RestaurantActorRole.SHOP_OWNER, null, null, hour)}) {
            assertThatThrownBy(() -> useCase.create(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Opening and closing times must both be present or both be absent");
            assertThat(persistence.calls).isZero();
        }
    }

    @Test
    void sameDayAndEqualHoursRemainValid() {
        var useCase = useCase(id -> { throw new AssertionError("No directory call expected"); });
        var open = LocalTime.of(8, 0);
        for (LocalTime close : new LocalTime[] {open, LocalTime.of(22, 0)}) {
            assertThat(useCase.create(command(101L, 7L, RestaurantActorRole.SHOP_OWNER,
                    null, open, close))).isSameAs(persistence.result);
        }
        assertThat(persistence.calls).isEqualTo(2);
    }

    private DefaultCreateRestaurantUseCase useCase(PrincipalOwnershipDirectory directory) {
        return new DefaultCreateRestaurantUseCase(
                new DefaultRestaurantOwnerAssignmentUseCase(directory), persistence);
    }

    private CreateRestaurantCommand adminCommand() {
        return command(1L, 7L, RestaurantActorRole.ADMIN, 42L, null, null);
    }

    private CreateRestaurantCommand command(Long actor, Long creator, RestaurantActorRole role,
            Long owner, LocalTime open, LocalTime close) {
        return new CreateRestaurantCommand(actor, creator, role, owner, "Restaurant", "123 Main Street",
                "0123456789", open, close, null, "image.png", 10.78, 106.69, "Description");
    }

    private void assertViolation(Runnable action, OwnerAssignmentFailure expected) {
        assertThatThrownBy(action::run).isInstanceOf(OwnerAssignmentException.class)
                .satisfies(error -> assertThat(((OwnerAssignmentException) error).failure()).isEqualTo(expected));
        assertThat(persistence.calls).isZero();
    }

    private static final class RecordingCreationPort implements RestaurantCreationPort {
        private int calls;
        private CreateRestaurantCommand command;
        private long ownerPrincipalId;
        private final CreateRestaurantResult result = new CreateRestaurantResult(99L, "Restaurant",
                "123 Main Street", "0123456789", null, null, 30, "image.png", "Description",
                10.78, 106.69, 0.0, 0, RestaurantStatus.ACTIVE, 0L, "Asia/Ho_Chi_Minh");

        @Override
        public CreateRestaurantResult create(CreateRestaurantCommand command, long ownerPrincipalId) {
            this.calls++;
            this.command = command;
            this.ownerPrincipalId = ownerPrincipalId;
            return result;
        }
    }
}
