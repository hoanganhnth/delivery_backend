package com.delivery.flashsale.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.delivery.flashsale.domain.FlashSaleReservationPolicy.*;

class FlashSaleReservationPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026,1,1,12,0);
    @Test void stockLookupTtlAndLedgerPresencePreserveExistingContract() {
        assertThat(expiresAt(now)).isEqualTo(now.plusMinutes(15));
        requireAllItems(2, 2);
        assertThatThrownBy(() -> requireAllItems(1, 2)).hasMessage("Flash sale item not found");
        requireLedgerItem(true);
        assertThatThrownBy(() -> requireLedgerItem(false)).hasMessage("Flash sale stock ledger is inconsistent");
        var request = mock(FlashSaleInputs.Quote.class);
        doReturn(List.of(line(2L, 2), line(1L, 1))).when(request).getItems();
        assertThat(requestedLines(request).keySet()).containsExactly(1L, 2L);
        doReturn(List.of(line(1L, 1), line(1L, 2))).when(request).getItems();
        assertThatThrownBy(() -> requestedLines(request)).hasMessage("Duplicate flashSaleItemId");
    }
    @ParameterizedTest @EnumSource(State.class)
    void completeTransitionMatrixAndInclusiveExpiry(State state) {
        for (LocalDateTime expiry : List.of(now.minusNanos(1), now, now.plusNanos(1))) {
            if (state == State.RESERVED && expiry.isAfter(now)) assertThat(commit(state,expiry,()->now)).isTrue();
            else if (state == State.COMMITTED) assertThat(commit(state,expiry,()->{throw new AssertionError("clock");})).isFalse();
            else assertThatThrownBy(()->commit(state,expiry,()->now)).isInstanceOf(IllegalArgumentException.class);
            assertThat(release(state)).isEqualTo(state == State.RESERVED || state == State.COMMITTED);
            assertThat(expire(state,expiry,()->now)).isEqualTo(state == State.RESERVED && !expiry.isAfter(now));
        }
    }
    @Test void validationOfRequestAndPrincipalBeforeIdentityAccess() {
        assertThatThrownBy(()->validateReservation(null,false)).hasMessage("Invalid flash sale reservation request");
        var request = mock(FlashSaleInputs.Reservation.class);
        assertInvalid(request);
        when(request.getReservationId()).thenReturn(UUID.randomUUID()); assertInvalid(request);
        when(request.getOrderId()).thenReturn(null); assertInvalid(request);
        when(request.getOrderId()).thenReturn(0L); assertInvalid(request);
        when(request.getOrderId()).thenReturn(1L); assertInvalid(request);
        when(request.getUserId()).thenReturn(null); assertInvalid(request);
        when(request.getUserId()).thenReturn(0L); assertInvalid(request);
        when(request.getUserId()).thenReturn(2L); assertInvalid(request);
        when(request.getRestaurantId()).thenReturn(null); assertInvalid(request);
        when(request.getRestaurantId()).thenReturn(0L); assertInvalid(request);
        when(request.getRestaurantId()).thenReturn(3L); assertInvalid(request);
        doReturn(List.of(line(4L,1))).when(request).getItems();
        validateReservation(request,false);
        when(request.getUserPrincipalId()).thenReturn(null);
        assertThatThrownBy(()->validateReservation(request,true)).hasMessage("userPrincipalId is required when principal ownership is enforced");
        when(request.getUserPrincipalId()).thenReturn(0L);
        assertThatThrownBy(()->validateReservation(request,true)).hasMessage("userPrincipalId is required when principal ownership is enforced");
        when(request.getUserPrincipalId()).thenReturn(5L); validateReservation(request,true);
        when(request.getUserPrincipalId()).thenReturn(-1L); validateReservation(request,false); // service compatibility; DTO rejects this.
    }
    private void assertInvalid(FlashSaleInputs.Reservation request) {
        assertThatThrownBy(()->validateReservation(request,false)).hasMessage("Invalid flash sale reservation request");
    }
    @Test void quoteAndMalformedLines() {
        assertThatThrownBy(()->validateQuote(null)).hasMessage("Invalid flash-sale quote request");
        var request = mock(FlashSaleInputs.Quote.class);
        when(request.getRestaurantId()).thenReturn(null);
        assertThatThrownBy(()->validateQuote(request)).hasMessage("Invalid flash-sale quote request");
        when(request.getRestaurantId()).thenReturn(0L);
        doReturn(List.of(line(1L,1))).when(request).getItems();
        assertThatThrownBy(()->validateQuote(request)).hasMessage("Invalid flash-sale quote request");
        when(request.getRestaurantId()).thenReturn(1L);
        assertThat(hasValidLines(null)).isFalse(); assertThat(hasValidLines(List.of())).isFalse();
        for (List<FlashSaleInputs.Line> lines : List.of(Collections.<FlashSaleInputs.Line>singletonList(null),
                List.of(line(null,1)),List.of(line(0L,1)),List.of(line(1L,null)),List.of(line(1L,0)),List.of(line(1L,1),line(1L,2)))) {
            assertThat(hasValidLines(lines)).isFalse();
            doReturn(lines).when(request).getItems();
            assertThatThrownBy(()->validateQuote(request)).hasMessage("Invalid flash-sale quote request");
        }
        doReturn(null).when(request).getItems();
        assertThatThrownBy(()->validateQuote(request)).hasMessage("Invalid flash-sale quote request");
        doReturn(List.of(line(1L,1))).when(request).getItems(); validateQuote(request);
    }
    @Test void replayIdentityChecksEveryFieldAndLineSetWithoutDependingOnOrderOrPrice() {
        var id=UUID.randomUUID();
        var request=mock(FlashSaleInputs.Reservation.class);
        when(request.getReservationId()).thenReturn(id); when(request.getOrderId()).thenReturn(1L);
        when(request.getUserId()).thenReturn(2L); when(request.getUserPrincipalId()).thenReturn(3L);
        when(request.getRestaurantId()).thenReturn(4L);
        doReturn(List.of(line(6L,2),line(5L,1))).when(request).getItems();
        var identity=new Identity(id,1L,2L,3L,4L,Map.of(5L,1,6L,2));
        requireExactReplay(identity,request);
        for (Identity changed : List.of(new Identity(UUID.randomUUID(),1L,2L,3L,4L,identity.lines()),
                new Identity(id,9L,2L,3L,4L,identity.lines()),new Identity(id,1L,9L,3L,4L,identity.lines()),
                new Identity(id,1L,2L,null,4L,identity.lines()),new Identity(id,1L,2L,3L,9L,identity.lines()),
                new Identity(id,1L,2L,3L,4L,Map.of(5L,2,6L,2))))
            assertThatThrownBy(()->requireExactReplay(changed,request)).hasMessage("Reservation replay payload does not match");
        doReturn(List.of(line(5L,1),line(5L,1))).when(request).getItems();
        assertThatThrownBy(()->requireExactReplay(identity,request)).hasMessage("Duplicate flashSaleItemId");
    }
    @Test void lockAndLedgerCompatibility() {
        for (Long order : new Long[]{null,0L,-1L}) assertThatThrownBy(()->validateLock(UUID.randomUUID(),order)).hasMessage("reservationId and positive orderId are required");
        assertThatThrownBy(()->validateLock(null,1L)).hasMessage("reservationId and positive orderId are required");
        validateLock(UUID.randomUUID(),1L); requireOrder(1L,1L);
        assertThatThrownBy(()->requireOrder(1L,2L)).hasMessage("reservationId is bound to another order");
        requireLedger(2,2);
        assertThatThrownBy(()->requireLedger(0,1)).hasMessage("Flash sale stock ledger is inconsistent");
        assertThatThrownBy(()->requireLedger(null,1)).isInstanceOf(NullPointerException.class);
    }
    private static FlashSaleInputs.Line line(Long id,Integer quantity) {
        var line=mock(FlashSaleInputs.Line.class); when(line.getFlashSaleItemId()).thenReturn(id); when(line.getQuantity()).thenReturn(quantity); return line;
    }
}
