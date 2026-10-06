package com.delivery.livestream_service.checkout;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.StreamProvider;
import com.delivery.livestream_service.repository.LivestreamCheckoutReceiptRepository;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import com.delivery.livestream_service.service.LivestreamCheckoutQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkout_receipts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.kafka.listener.auto-startup=false"
})
class LivestreamCheckoutReceiptIntegrationTest {
    @Autowired LivestreamCheckoutQuoteService service;
    @Autowired LivestreamRepository rooms;
    @Autowired LivestreamProductRepository products;
    @Autowired LivestreamCheckoutReceiptRepository receipts;

    @Test
    void retryReturnsTheOriginalPriceSnapshotAfterThePinnedProductIsRetired() {
        Livestream room = new Livestream();
        room.setSellerId(7L); room.setRestaurantId(42L); room.setTitle("Receipt test");
        room.setStatus(LivestreamStatus.LIVE); room.setStreamProvider(StreamProvider.AGORA);
        room = rooms.saveAndFlush(room);
        LivestreamProduct product = new LivestreamProduct();
        product.setLivestreamId(room.getId()); product.setProductId(10L); product.setRestaurantId(42L);
        product.setIsPinned(true); product.setPriceAtLive(new BigDecimal("99000"));
        product = products.saveAndFlush(product);
        LivestreamCheckoutQuoteRequest request = request(room);

        var first = service.orderContext(request, 123L, "corr-first", "checkout-1");
        product.setPriceAtLive(new BigDecimal("120000"));
        product.setDeletedAt(LocalDateTime.now());
        products.saveAndFlush(product);

        var replay = service.orderContext(request, 123L, "corr-retry", "checkout-1");

        assertThat(replay).isEqualTo(first);
        assertThat(replay).singleElement().satisfies(context -> {
            assertThat(context.priceAtLive()).isEqualByComparingTo("99000");
            assertThat(context.correlationId()).isEqualTo("corr-first");
        });
        assertThat(receipts.findByActorPrincipalIdAndIdempotencyKey(123L, "checkout-1")).isPresent();
    }

    @Test
    void concurrentConflictingPayloadsLeaveOneReceiptAndRejectTheLoser() throws Exception {
        Livestream room = liveRoom();
        pinnedProduct(room, 10L, "99000");
        pinnedProduct(room, 20L, "120000");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> invokeWhenStarted(ready, start, request(room, 10L)));
            var second = executor.submit(() -> invokeWhenStarted(ready, start, request(room, 20L)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult)).anySatisfy(result ->
                    assertThat(result).isInstanceOf(List.class));
            assertThat(List.of(firstResult, secondResult)).anySatisfy(result ->
                    assertThat(result).isInstanceOf(IllegalArgumentException.class));
            assertThat(receipts.findByActorPrincipalIdAndIdempotencyKey(456L, "race-key")).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private Object invokeWhenStarted(CountDownLatch ready, CountDownLatch start,
                                     LivestreamCheckoutQuoteRequest request) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
            return service.orderContext(request, 456L, "corr", "race-key");
        } catch (Exception exception) {
            return exception;
        }
    }

    private Livestream liveRoom() {
        Livestream room = new Livestream();
        room.setSellerId(7L); room.setRestaurantId(42L); room.setTitle("Race test");
        room.setStatus(LivestreamStatus.LIVE); room.setStreamProvider(StreamProvider.AGORA);
        String suffix = UUID.randomUUID().toString();
        room.setRoomId("receipt-room-" + suffix);
        room.setChannelName("receipt-channel-" + suffix);
        return rooms.saveAndFlush(room);
    }

    private void pinnedProduct(Livestream room, long productId, String price) {
        LivestreamProduct product = new LivestreamProduct();
        product.setLivestreamId(room.getId()); product.setProductId(productId); product.setRestaurantId(42L);
        product.setIsPinned(true); product.setPriceAtLive(new BigDecimal(price));
        products.saveAndFlush(product);
    }

    private LivestreamCheckoutQuoteRequest request(Livestream room) {
        return request(room, 10L);
    }

    private LivestreamCheckoutQuoteRequest request(Livestream room, long productId) {
        LivestreamCheckoutQuoteRequest request = new LivestreamCheckoutQuoteRequest();
        request.setLivestreamId(room.getId()); request.setRestaurantId(room.getRestaurantId());
        request.setProductIds(List.of(productId));
        return request;
    }
}
