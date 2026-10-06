package com.delivery.livestream.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutValidationPolicyTest {
    private static final UUID ROOM = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final BigDecimal PRICE = new BigDecimal("0.00100");

    static Stream<Arguments> invalidScopes() {
        return Stream.of(
                Arguments.of(null, 42L, List.of(1L)),
                Arguments.of(ROOM, null, List.of(1L)),
                Arguments.of(ROOM, 0L, List.of(1L)),
                Arguments.of(ROOM, -1L, List.of(1L)),
                Arguments.of(ROOM, 42L, null),
                Arguments.of(ROOM, 42L, List.of()),
                Arguments.of(ROOM, 42L, LongStream.rangeClosed(1, 51).boxed().toList()),
                Arguments.of(ROOM, 42L, Arrays.asList(1L, null)),
                Arguments.of(ROOM, 42L, List.of(0L)),
                Arguments.of(ROOM, 42L, List.of(-1L)),
                Arguments.of(ROOM, 42L, List.of(2L, 1L, 2L)));
    }

    @ParameterizedTest
    @MethodSource("invalidScopes")
    void rejectsEveryInvalidScope(UUID room, Long restaurant, List<Long> products) {
        assertThatThrownBy(() -> CheckoutValidationPolicy.requireScope(room, restaurant, products))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid livestream checkout quote scope");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void acceptsInclusiveBoundsWithoutMutatingProductSequence(int size) {
        var products = LongStream.rangeClosed(1, size).map(id -> size + 1 - id).boxed().toList();
        var original = List.copyOf(products);
        CheckoutValidationPolicy.requireScope(ROOM, 1L, products);
        assertThat(products).containsExactlyElementsOf(original);
    }

    @Test
    void acceptsLargePositiveIdsWithoutNarrowing() {
        CheckoutValidationPolicy.requireScope(ROOM, Long.MAX_VALUE, List.of(Long.MAX_VALUE));
    }

    static Stream<Arguments> invalidMetadata() {
        return Stream.of(
                Arguments.of(null, "correlation", "key"),
                Arguments.of(0L, "correlation", "key"),
                Arguments.of(-1L, "correlation", "key"),
                Arguments.of(1L, null, "key"),
                Arguments.of(1L, "", "key"),
                Arguments.of(1L, " \t\n", "key"),
                Arguments.of(1L, "correlation", null),
                Arguments.of(1L, "correlation", ""),
                Arguments.of(1L, "correlation", " \t\n"));
    }

    @ParameterizedTest
    @MethodSource("invalidMetadata")
    void rejectsEveryMissingContextIdentity(Long actor, String correlation, String key) {
        assertThatThrownBy(() -> CheckoutValidationPolicy.requireContextMetadata(actor, correlation, key))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Livestream checkout context requires actor, correlation and idempotency key");
    }

    @Test
    void acceptsNonBlankMetadataWithoutAddingLengthOrWhitespaceRestrictions() {
        CheckoutValidationPolicy.requireContextMetadata(Long.MAX_VALUE, " correlation ", " k".repeat(300));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-0.01"})
    void rejectsInvalidQuotePrices(String value) {
        BigDecimal price = value == null || value.isEmpty() ? null : new BigDecimal(value);
        assertThatThrownBy(() -> CheckoutValidationPolicy.requireQuoteProduct(42L, 42L, price))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Pinned product price is invalid");
    }

    @ParameterizedTest
    @MethodSource("foreignRestaurants")
    void rejectsQuoteScopeBeforePrice(Long restaurant) {
        assertThatThrownBy(() -> CheckoutValidationPolicy.requireQuoteProduct(42L, restaurant, null))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Pinned product restaurant scope is invalid");
    }

    static Stream<Long> foreignRestaurants() { return Stream.of(null, 43L); }

    @Test
    void acceptsPositiveQuotePriceWithoutRounding() {
        CheckoutValidationPolicy.requireQuoteProduct(42L, 42L, PRICE);
        assertThat(PRICE.toPlainString()).isEqualTo("0.00100");
    }

    static Stream<Arguments> invalidSnapshots() {
        return Stream.of(
                Arguments.of(null, PRICE, 42L),
                Arguments.of(1L, null, 42L),
                Arguments.of(1L, BigDecimal.ZERO, 42L),
                Arguments.of(1L, new BigDecimal("-0.001"), 42L),
                Arguments.of(1L, PRICE, null),
                Arguments.of(1L, PRICE, 43L));
    }

    @ParameterizedTest
    @MethodSource("invalidSnapshots")
    void rejectsEveryInvalidContextSnapshot(Long id, BigDecimal price, Long restaurant) {
        assertThatThrownBy(() -> CheckoutValidationPolicy.requireContextProduct(id, price, 42L, restaurant))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Pinned product snapshot is invalid");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1, Long.MAX_VALUE})
    void preservesExistingNonNullSnapshotIdRule(long id) {
        CheckoutValidationPolicy.requireContextProduct(id, PRICE, 42L, 42L);
    }
}
