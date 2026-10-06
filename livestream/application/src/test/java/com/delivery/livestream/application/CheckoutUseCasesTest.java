package com.delivery.livestream.application;

import com.delivery.livestream.api.*;
import com.delivery.livestream.domain.CheckoutFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutUseCasesTest {
    private final UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final CheckoutCommand command = new CheckoutCommand(id, 42L, List.of(20L, 10L));
    @SuppressWarnings("unchecked")
    private final CheckoutPorts<String, String, String, String, String, String> ports = mock(CheckoutPorts.class);
    private final CheckoutUseCases<String, String, String, String, String, String> useCases = new CheckoutUseCases<>(ports);

    private void room(String status, Long restaurant) {
        when(ports.room(id)).thenReturn("room");
        when(ports.roomSnapshot("room")).thenReturn(new RoomSnapshot(id, 7L, restaurant, status, null));
    }

    private void pins(List<String> pins) {
        when(ports.pins(id, command.products())).thenReturn(pins);
        when(ports.snapshot("ten")).thenReturn(new ProductSnapshot(1L, 10L, 42L, BigDecimal.TEN, true));
        // Preserve the pre-existing acceptance of non-positive snapshot IDs.
        when(ports.snapshot("twenty")).thenReturn(new ProductSnapshot(-1L, 20L, 42L, BigDecimal.ONE, true));
    }

    @Test
    void invalidScopeAndMetadataPrecedeEveryPort() {
        assertThatThrownBy(() -> useCases.quote(null)).hasMessage("Invalid livestream checkout quote scope");
        assertThatThrownBy(() -> useCases.context(null, null, null, null))
                .hasMessage("Livestream checkout context requires actor, correlation and idempotency key");
        assertThatThrownBy(() -> useCases.context(null, 1L, "correlation", "key"))
                .hasMessage("Invalid livestream checkout quote scope");
        assertThatThrownBy(() -> useCases.quote(new CheckoutCommand(id, 42L, List.of(10L, 10L))))
                .hasMessage("Invalid livestream checkout quote scope");
        verifyNoInteractions(ports);
    }

    @Test
    void quoteRestoresRequestOrderAndOmitsMissingOrdinaryItems() {
        room("LIVE", 42L);
        pins(List.of("ten", "twenty"));
        when(ports.item("twenty")).thenReturn("item20");
        when(ports.item("ten")).thenReturn("item10");
        when(ports.quote(id, 42L, List.of("item20", "item10"))).thenReturn("quote");
        assertThat(useCases.quote(command)).isEqualTo("quote");
        var order = inOrder(ports);
        order.verify(ports).room(id);
        order.verify(ports).roomSnapshot("room");
        order.verify(ports).pins(id, command.products());
        order.verify(ports).item("twenty");
        order.verify(ports).item("ten");
        pins(List.of("ten"));
        useCases.quote(command);
        verify(ports).quote(id, 42L, List.of("item10"));
        pins(List.of());
        useCases.quote(command);
        verify(ports).quote(id, 42L, List.of());
        verify(ports, never()).receipt(any(), any());
    }

    @Test
    void roomStatusPrecedesRestaurantAndPinLookupForBothOperations() {
        room("ENDED", 43L);
        assertThatThrownBy(() -> useCases.quote(command)).hasMessage("Giá livestream chỉ áp dụng khi phòng đang LIVE");
        assertThatThrownBy(() -> useCases.context(command, 1L, "c", "key")).hasMessage("Checkout chỉ áp dụng khi phòng đang LIVE");
        room("LIVE", 43L);
        assertThatThrownBy(() -> useCases.quote(command)).hasMessage("Restaurant không thuộc livestream");
        assertThatThrownBy(() -> useCases.context(command, 1L, "c", "key")).hasMessage("Restaurant không thuộc livestream");
        verify(ports, never()).pins(any(), any());
        verify(ports, never()).store(any(), any(), any(), any());
    }

    @Test
    void quoteScopeFailurePrecedesPriceFailure() {
        room("LIVE", 42L);
        pins(List.of("ten"));
        when(ports.snapshot("ten")).thenReturn(new ProductSnapshot(null, 10L, 43L, null, true));
        assertThatThrownBy(() -> useCases.quote(command)).hasMessage("Pinned product restaurant scope is invalid");
        when(ports.snapshot("ten")).thenReturn(new ProductSnapshot(null, 10L, 42L, BigDecimal.ZERO, true));
        assertThatThrownBy(() -> useCases.quote(command)).hasMessage("Pinned product price is invalid");
        verify(ports, never()).item(any());
        verify(ports, never()).quote(any(), any(), any());
    }

    @Test
    void replayUsesOriginalPayloadAndIgnoresCurrentRoomAndCorrelation() {
        String fingerprint = CheckoutFingerprint.of(id, 42L, command.products(), 1L);
        when(ports.receipt(1L, "key")).thenReturn(Optional.of("receipt"));
        when(ports.fingerprint("receipt")).thenReturn(fingerprint);
        when(ports.restore("receipt")).thenReturn(List.of("original"));
        assertThat(useCases.context(command, 1L, "new-correlation", "key")).containsExactly("original");
        verify(ports, never()).room(any());
        verify(ports, never()).pins(any(), any());
        verify(ports, never()).store(any(), any(), any(), any());
        when(ports.fingerprint("receipt")).thenReturn("conflict");
        clearInvocations(ports);
        assertThatThrownBy(() -> useCases.context(command, 1L, "c", "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency key was already used for another checkout context");
        verify(ports, never()).restore(any());
    }

    @Test
    void freshContextStoresOrderedCompleteSnapshotAndReturnsRecoveredResult() {
        room("LIVE", 42L);
        pins(List.of("ten", "twenty"));
        when(ports.context("room", "twenty", 1L, "c", "key")).thenReturn("context20");
        when(ports.context("room", "ten", 1L, "c", "key")).thenReturn("context10");
        String fingerprint = CheckoutFingerprint.of(id, 42L, command.products(), 1L);
        when(ports.store(1L, "key", fingerprint, List.of("context20", "context10"))).thenReturn(List.of("committed"));
        assertThat(useCases.context(command, 1L, "c", "key")).containsExactly("committed");
        var order = inOrder(ports);
        order.verify(ports).receipt(1L, "key");
        order.verify(ports).room(id);
        order.verify(ports).pins(id, command.products());
        order.verify(ports).context("room", "twenty", 1L, "c", "key");
        order.verify(ports).context("room", "ten", 1L, "c", "key");
        order.verify(ports).store(1L, "key", fingerprint, List.of("context20", "context10"));
    }

    @Test
    void incompleteContextRejectsBeforeMappingAnySnapshot() {
        room("LIVE", 42L);
        pins(List.of("ten"));
        assertThatThrownBy(() -> useCases.context(command, 1L, "c", "key"))
                .hasMessage("Một hoặc nhiều sản phẩm livestream không còn khả dụng");
        verify(ports, never()).context(any(), any(), any(), any(), any());
        verify(ports, never()).store(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "price", "scope"})
    void invalidContextSnapshotNeverStores(String invalid) {
        room("LIVE", 42L);
        pins(List.of("ten", "twenty"));
        when(ports.snapshot("twenty")).thenReturn(new ProductSnapshot(
                invalid.equals("id") ? null : 1L, 20L, invalid.equals("scope") ? 43L : 42L,
                invalid.equals("price") ? null : BigDecimal.ONE, true));
        assertThatThrownBy(() -> useCases.context(command, 1L, "c", "key")).hasMessage("Pinned product snapshot is invalid");
        verify(ports, never()).context(any(), any(), any(), any(), any());
        verify(ports, never()).store(any(), any(), any(), any());
    }

    @Test
    void duplicateRowsRetainCollectorFailureRatherThanSilentlyChoosingOne() {
        room("LIVE", 42L);
        pins(List.of("ten", "ten"));
        assertThatThrownBy(() -> useCases.quote(command)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate key");
        verify(ports, never()).quote(any(), any(), any());
    }
}
