package com.delivery.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Phase8RolloutPropertiesTest {
    @Test
    void defaultsToDisabledSafeRolloutAndAcceptsBoundedPercentage() {
        var properties = new Phase8RolloutProperties();

        assertThat(properties.isShadowReads()).isFalse();
        assertThat(properties.isWritesEnabled()).isFalse();
        assertThat(properties.getTrafficPercentage()).isZero();
        properties.setTrafficPercentage(25);
        assertThat(properties.getTrafficPercentage()).isEqualTo(25);
    }

    @Test
    void rejectsTrafficOutsidePercentageRange() {
        var properties = new Phase8RolloutProperties();
        assertThatThrownBy(() -> properties.setTrafficPercentage(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setTrafficPercentage(101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
