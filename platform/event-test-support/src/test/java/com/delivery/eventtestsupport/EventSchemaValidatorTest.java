package com.delivery.eventtestsupport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.delivery.eventtestsupport.EventSchemaValidator.optional;
import static com.delivery.eventtestsupport.EventSchemaValidator.required;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSchemaValidatorTest {

    @Test
    void validatesRequiredFieldsAndJsonTypes() {
        EventSchemaValidator.ValidationResult result = EventSchemaValidator.validate(
                EventFixtures.orderCreated(), List.of(
                        required("eventId", java.util.UUID.class),
                        required("orderId", Long.class),
                        required("totalPrice", BigDecimal.class),
                        required("createdAt", java.time.Instant.class),
                        required("restaurantName", String.class)));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void reportsMissingAndWrongTypeFields() {
        EventSchemaValidator.ValidationResult result = EventSchemaValidator.validate(
                EventFixtures.orderCreated(), Map.of(
                        "doesNotExist", String.class,
                        "orderId", String.class));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactlyInAnyOrder(
                "missing required field: doesNotExist",
                "field orderId expected String but was NUMBER");
    }

    @Test
    void allowsNullOptionalFieldsAndRejectsNullRequiredFields() {
        TestEvents.PaymentEvent payment = EventFixtures.paymentFailed();

        assertThat(EventSchemaValidator.validate(payment, List.of(
                required("status", String.class),
                optional("transactionId", String.class)))).satisfies(result -> {
                    assertThat(result.valid()).isTrue();
                    assertThat(result.errors()).isEmpty();
                });

        assertThat(EventSchemaValidator.validate(payment, List.of(
                required("transactionId", String.class)))).satisfies(result -> {
                    assertThat(result.valid()).isFalse();
                    assertThat(result.errors()).containsExactly("required field is null: transactionId");
                });
    }

    @Test
    void assertValidRaisesUsefulAssertionError() {
        assertThatThrownBy(() -> EventSchemaValidator.assertValid(EventFixtures.shipperFound(), Map.of(
                "availableShippers", String.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("availableShippers expected String");
    }
}
