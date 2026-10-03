package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.TrackingAccessDeniedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultShipperIdentityUseCaseTest {
    @Test void projectsCanonicalShipperRatherThanPrincipalOrLegacyIdentityWithEitherGate() {
        var core = new DefaultShipperIdentityUseCase(principal -> {
            assertThat(principal).isEqualTo(100L);
            return Optional.of(new ShipperIdentityFacts(200L, 987L));
        });
        for (boolean enforced : new boolean[]{false, true}) {
            assertThat(core.resolve(100L, 200L, enforced)).isEqualTo(new ShipperIdentityResolution(987L, false));
        }
    }
    @Test void divergentProjectionNeverFallsBackEvenBeforeEnforcement() {
        var core = new DefaultShipperIdentityUseCase(id -> Optional.of(new ShipperIdentityFacts(201L, 987L)));
        for (boolean enforced : new boolean[]{false, true}) {
            assertThatThrownBy(() -> core.resolve(100L, 200L, enforced))
                    .isInstanceOf(TrackingAccessDeniedException.class).hasMessage("Shipper identity projection is divergent");
        }
    }
    @Test void missingProjectionAllowsOnlyThePreEnforcementLegacyFallback() {
        var core = new DefaultShipperIdentityUseCase(id -> Optional.empty());
        assertThat(core.resolve(100L, 200L, false)).isEqualTo(new ShipperIdentityResolution(200L, true));
        assertThatThrownBy(() -> core.resolve(100L, 200L, true))
                .isInstanceOf(TrackingAccessDeniedException.class).hasMessage("Shipper identity projection is not ready");
    }
    @Test void invalidAuthenticatedTupleIsRejectedBeforeProjectionAccess() {
        var core = new DefaultShipperIdentityUseCase(id -> { throw new AssertionError("No projection read expected"); });
        for (Long invalid : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> core.resolve(invalid, 200L, false))
                    .isInstanceOf(TrackingAccessDeniedException.class).hasMessage("Missing authenticated shipper identity");
            assertThatThrownBy(() -> core.resolve(100L, invalid, false))
                    .isInstanceOf(TrackingAccessDeniedException.class).hasMessage("Missing authenticated shipper identity");
        }
    }
    @Test void projectionFailureCannotBecomeFallback() {
        var failure = new IllegalStateException("Database unavailable");
        var core = new DefaultShipperIdentityUseCase(id -> { throw failure; });
        assertThatThrownBy(() -> core.resolve(100L, 200L, false)).isSameAs(failure);
        assertThatThrownBy(() -> new DefaultShipperIdentityUseCase(null)).isInstanceOf(NullPointerException.class);
    }
}
