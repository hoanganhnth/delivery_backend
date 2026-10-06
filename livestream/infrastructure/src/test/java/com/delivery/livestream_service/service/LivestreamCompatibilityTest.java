package com.delivery.livestream_service.service;

import com.delivery.livestream.domain.LivestreamPolicy;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.ProductAlreadyPinnedException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LivestreamCompatibilityTest {
    @Test
    void translatesOnlyDomainRejectionsToExactLegacyClassesAndMessages() {
        assertThatThrownBy(() -> LivestreamCompatibility.run(() -> LivestreamPolicy.start("LIVE")))
                .isExactlyInstanceOf(InvalidLivestreamStatusException.class)
                .hasMessage("Không thể bắt đầu livestream. Trạng thái hiện tại: LIVE");
        assertThatThrownBy(() -> LivestreamCompatibility.run(() -> LivestreamPolicy.seller(7L, 8L, false)))
                .isExactlyInstanceOf(UnauthorizedLivestreamAccessException.class)
                .hasMessage("Bạn không có quyền thao tác với livestream này");
        assertThatThrownBy(() -> LivestreamCompatibility.run(() -> LivestreamPolicy.duplicate(true)))
                .isExactlyInstanceOf(ProductAlreadyPinnedException.class)
                .hasMessage("Sản phẩm đã được pin trong livestream");
        var infrastructureFailure = new IllegalStateException("persistence failed");
        assertThatThrownBy(() -> LivestreamCompatibility.call(() -> { throw infrastructureFailure; }))
                .isSameAs(infrastructureFailure);
        assertThat(LivestreamCompatibility.call(() -> "mapped")).isEqualTo("mapped");
    }

    @Test
    void nullableEntityValuesArePreservedRatherThanDefaultedByAdapters() {
        var room = new Livestream();
        room.setStatus(null);
        room.setViewCount(null);
        assertThat(LivestreamCompatibility.snapshot(room).status()).isNull();
        assertThat(LivestreamCompatibility.snapshot(room).viewCount()).isNull();
        room.setStatus(LivestreamStatus.LIVE);
        assertThat(LivestreamCompatibility.snapshot(room).status()).isEqualTo("LIVE");
        var product = new LivestreamProduct();
        product.setId(-1L);
        product.setIsPinned(null);
        assertThat(LivestreamCompatibility.snapshot(product).id()).isEqualTo(-1L);
        assertThat(LivestreamCompatibility.snapshot(product).pinned()).isNull();
    }
}
