package com.delivery.promotion.application.api;

import com.delivery.promotion.domain.VoucherSelectionMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PromotionCommandsTest {
    @Test void contractsRetainIdentityMoneyAndClockWithoutNormalization() {
        UUID id = UUID.randomUUID();
        var ids = List.of(3L, 1L);
        var reserve = new PromotionCommands.Reserve(id, 5L, ids);
        assertThat(reserve.reservationId()).isEqualTo(id);
        assertThat(reserve.orderId()).isEqualTo(5L);
        assertThat(reserve.voucherIds()).isEqualTo(ids);
        var transition = new PromotionCommands.Transition(true, false);
        assertThat(transition.bulk()).isTrue(); assertThat(transition.commit()).isFalse();
        var now = LocalDateTime.of(2026, 10, 5, 12, 0);
        var state = new PromotionCommands.ReservationState(null, now);
        assertThat(state.state()).isNull(); assertThat(state.expiresAt()).isEqualTo(now);
        var money = new BigDecimal("1.234");
        var pricing = new PromotionCommands.Pricing(List.of(), 7L, money, money, ids, VoucherSelectionMode.MANUAL, now);
        assertThat(pricing.subtotal()).isSameAs(money); assertThat(pricing.shipping()).isSameAs(money);
        assertThat(pricing.restaurantId()).isEqualTo(7L); assertThat(pricing.selectedIds()).isEqualTo(ids);
        assertThat(pricing.mode()).isEqualTo(VoucherSelectionMode.MANUAL); assertThat(pricing.now()).isEqualTo(now);
        assertThat(pricing.vouchers()).isEmpty();
        var event = new PromotionCommands.OrderEvent(id, "order.created", "COMMIT", 5L, null, id, "", "hash");
        assertThat(event.eventId()).isEqualTo(id); assertThat(event.bulkReservationId()).isEqualTo(id);
        assertThat(event.legacyReservationId()).isNull(); assertThat(event.orderId()).isEqualTo(5L);
        assertThat(event.source()).isEqualTo("order.created"); assertThat(event.action()).isEqualTo("COMMIT");
        assertThat(event.previousStatus()).isEmpty(); assertThat(event.fingerprint()).isEqualTo("hash");
    }
}
