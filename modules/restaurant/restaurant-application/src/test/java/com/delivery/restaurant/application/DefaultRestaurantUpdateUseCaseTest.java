package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.RestaurantMutationPlan;
import com.delivery.restaurant.application.api.RestaurantStoredFacts;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import com.delivery.restaurant.application.api.RestaurantUpdatePort;
import com.delivery.restaurant.application.api.RestaurantUpdateResult;
import com.delivery.restaurant.application.api.UpdateRestaurantCommand;
import com.delivery.restaurant.application.api.UpdateRestaurantUseCase;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultRestaurantUpdateUseCaseTest {

    private final RecordingUpdatePort port = new RecordingUpdatePort();
    private final UpdateRestaurantUseCase useCase = new DefaultRestaurantUpdateUseCase(
            port, new DefaultRestaurantManagementAccessUseCase());

    @Test
    void ownerAndAdminMayUpdateEveryMutableField() {
        port.stored = stored(10L, 7L, 99L, LocalTime.of(9, 0), LocalTime.of(18, 0));
        UpdateRestaurantCommand ownerCommand = command(
                10L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true);

        assertThat(useCase.update(ownerCommand)).isPresent();
        assertThat(port.plan).isNotNull();
        assertThat(port.plan.restaurantId()).isEqualTo(10L);
        assertThat(port.plan.ownerPrincipalId()).isEqualTo(7L);
        assertThat(port.plan.name()).isEqualTo("New name");
        assertThat(port.plan.address()).isEqualTo("New address");
        assertThat(port.plan.phone()).isEqualTo("0123456789");
        assertThat(port.plan.openingHour()).isEqualTo(LocalTime.of(10, 0));
        assertThat(port.plan.closingHour()).isEqualTo(LocalTime.of(20, 0));
        assertThat(port.plan.defaultPrepTimeMinutes()).isEqualTo(45);
        assertThat(port.plan.image()).isEqualTo("new.png");
        assertThat(port.plan.description()).isEqualTo("New description");
        assertThat(port.plan.latitude()).isEqualTo(10.8);
        assertThat(port.plan.longitude()).isEqualTo(106.7);

        port.reset();
        port.stored = stored(10L, 99L, 99L, LocalTime.of(9, 0), LocalTime.of(18, 0));
        assertThat(useCase.update(command(
                10L, 1L, 1L, RestaurantActorRole.ADMIN, true))).isPresent();
        assertThat(port.plan.ownerPrincipalId()).isEqualTo(99L);
    }

    @Test
    void partialHourUpdateMergesWithStoredPairBeforePlanning() {
        port.stored = stored(10L, 7L, 700L, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThat(useCase.update(new UpdateRestaurantCommand(
                10L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true,
                null, null, null, LocalTime.of(10, 0), null,
                null, null, null, null, null))).isPresent();

        assertThat(port.plan.openingHour()).isEqualTo(LocalTime.of(10, 0));
        assertThat(port.plan.closingHour()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void legacyOwnerMayUpdateBeforeEnforcementAndClaimsPrincipal() {
        port.stored = stored(10L, null, 700L, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThat(useCase.update(command(
                10L, 101L, 700L, RestaurantActorRole.SHOP_OWNER, false))).isPresent();

        assertThat(port.plan.ownerPrincipalId()).isEqualTo(101L);
    }

    @Test
    void missingRowReturnsEmptyWithoutPlanning() {
        port.stored = null;

        assertThat(useCase.update(command(
                404L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true))).isEmpty();
        assertThat(port.invocations).isEqualTo(1);
        assertThat(port.plan).isNull();
    }

    @Test
    void foreignOwnerAndEnforcedLegacyRowNeverProduceMutationPlan() {
        port.stored = stored(10L, 99L, 700L, LocalTime.of(9, 0), LocalTime.of(18, 0));
        assertThatThrownBy(() -> useCase.update(command(
                10L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.plan).isNull();

        port.reset();
        port.stored = stored(10L, null, 700L, LocalTime.of(9, 0), LocalTime.of(18, 0));
        assertThatThrownBy(() -> useCase.update(command(
                10L, 101L, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.plan).isNull();
    }

    @Test
    void missingOrUnsupportedActorNeverReachesUpdatePort() {
        assertThatThrownBy(() -> useCase.update(command(
                10L, null, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> useCase.update(command(
                10L, 7L, null, RestaurantActorRole.SHOP_OWNER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> useCase.update(command(
                10L, 7L, 700L, RestaurantActorRole.OTHER, true)))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.invocations).isZero();
    }

    @Test
    void incompleteMergedScheduleNeverProducesMutationPlan() {
        port.stored = stored(10L, 7L, 700L, null, null);

        assertThatThrownBy(() -> useCase.update(new UpdateRestaurantCommand(
                10L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true,
                null, null, null, LocalTime.of(18, 0), null,
                null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opening and closing times must both be present or both be absent");
        assertThat(port.plan).isNull();
    }

    @Test
    void updatePortFailureIsVisibleAfterApplicationDecision() {
        port.failure = new IllegalStateException("outbox failed");
        port.stored = stored(10L, 7L, 700L, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThatThrownBy(() -> useCase.update(command(
                10L, 7L, 700L, RestaurantActorRole.SHOP_OWNER, true)))
                .isSameAs(port.failure);
        assertThat(port.plan).isNull();
    }

    private UpdateRestaurantCommand command(Long id, Long principal, Long legacy,
            RestaurantActorRole role, boolean enforced) {
        return new UpdateRestaurantCommand(id, principal, legacy, role, enforced,
                "New name", "New address", "0123456789", LocalTime.of(10, 0), LocalTime.of(20, 0),
                45, "new.png", 10.8, 106.7, "New description");
    }

    private RestaurantStoredFacts stored(Long id, Long owner, Long creator,
            LocalTime opening, LocalTime closing) {
        return new RestaurantStoredFacts(id, owner, creator, "Old name", "Old address", "0987654321",
                opening, closing, 30, "old.png", "Old description", 10.0, 106.0,
                "Asia/Ho_Chi_Minh");
    }

    private static final class RecordingUpdatePort implements RestaurantUpdatePort {
        private RestaurantStoredFacts stored;
        private RestaurantMutationPlan plan;
        private RuntimeException failure;
        private int invocations;

        @Override
        public Optional<RestaurantUpdateResult> update(UpdateRestaurantCommand command,
                com.delivery.restaurant.application.api.RestaurantUpdateDecision decision) {
            invocations++;
            if (failure != null) {
                throw failure;
            }
            if (stored == null) {
                return Optional.empty();
            }
            plan = decision.decide(stored);
            return Optional.of(new RestaurantUpdateResult(
                    new RestaurantSnapshot(plan.restaurantId(), plan.name(), plan.address(), plan.phone(),
                            plan.openingHour(), plan.closingHour(), plan.defaultPrepTimeMinutes(),
                            plan.image(), plan.description(), plan.latitude(), plan.longitude(), 4.5, 2,
                            RestaurantStatus.ACTIVE, 3L, stored.timeZone(), plan.ownerPrincipalId(), stored.creatorId()),
                    stored.ownerPrincipalId() == null && plan.ownerPrincipalId() != null));
        }

        private void reset() {
            stored = null;
            plan = null;
            failure = null;
            invocations = 0;
        }
    }
}
