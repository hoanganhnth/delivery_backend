package com.delivery.eventtestsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Validates the JSON field presence and primitive shape of an event payload. */
public final class EventSchemaValidator {

    private EventSchemaValidator() {
    }

    public static FieldSpec required(String name, Class<?> type) {
        return new FieldSpec(name, type, true);
    }

    public static FieldSpec optional(String name, Class<?> type) {
        return new FieldSpec(name, type, false);
    }

    public static ValidationResult validate(Object event, Map<String, Class<?>> expectedFields) {
        return validate(event, expectedFields, EventFixtures.objectMapper());
    }

    public static ValidationResult validate(Object event, Map<String, Class<?>> expectedFields,
                                            ObjectMapper mapper) {
        if (expectedFields == null) {
            throw new IllegalArgumentException("expectedFields are required");
        }
        Map<String, FieldSpec> fields = new LinkedHashMap<>();
        expectedFields.forEach((name, type) -> fields.put(name, required(name, type)));
        return validate(event, fields.values(), mapper);
    }

    public static ValidationResult validate(Object event, Collection<FieldSpec> expectedFields) {
        return validate(event, expectedFields, EventFixtures.objectMapper());
    }

    public static ValidationResult validate(Object event, Collection<FieldSpec> expectedFields,
                                            ObjectMapper mapper) {
        if (event == null || expectedFields == null || mapper == null) {
            throw new IllegalArgumentException("event, expectedFields and mapper are required");
        }
        List<String> errors = new ArrayList<>();
        JsonNode root;
        try {
            root = mapper.valueToTree(event);
        } catch (IllegalArgumentException exception) {
            return new ValidationResult(false, List.of("event could not be converted to JSON: "
                    + exception.getMessage()));
        }
        if (root == null || !root.isObject()) {
            return new ValidationResult(false, List.of("event must serialize to a JSON object"));
        }

        for (FieldSpec field : expectedFields) {
            if (field == null || field.name() == null || field.name().isBlank() || field.type() == null) {
                errors.add("field specification must contain a name and type");
                continue;
            }
            JsonNode value = root.get(field.name());
            if (value == null || value.isMissingNode()) {
                if (field.required()) errors.add("missing required field: " + field.name());
                continue;
            }
            if (value.isNull()) {
                if (field.required()) errors.add("required field is null: " + field.name());
                continue;
            }
            if (!matches(value, field.type())) {
                errors.add("field " + field.name() + " expected " + field.type().getSimpleName()
                        + " but was " + value.getNodeType());
            }
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    public static void assertValid(Object event, Map<String, Class<?>> expectedFields) {
        assertValid(event, validate(event, expectedFields));
    }

    public static void assertValid(Object event, Collection<FieldSpec> expectedFields) {
        assertValid(event, validate(event, expectedFields));
    }

    public static void assertValid(Object event, Collection<FieldSpec> expectedFields, ObjectMapper mapper) {
        assertValid(event, validate(event, expectedFields, mapper));
    }

    private static void assertValid(Object event, ValidationResult result) {
        if (!result.valid()) {
            throw new AssertionError("Invalid event schema for " + event.getClass().getName()
                    + ": " + String.join(", ", result.errors()));
        }
    }

    private static boolean matches(JsonNode node, Class<?> type) {
        if (type == Object.class) return true;
        if (type == String.class || type == Character.class || type == char.class
                || type == UUID.class || type == BigDecimal.class
                || type == java.time.Instant.class || type == java.time.LocalDateTime.class
                || Temporal.class.isAssignableFrom(type) || type.isEnum()) {
            return node.isTextual() || (type == BigDecimal.class && node.isNumber());
        }
        if (type == Boolean.class || type == boolean.class) return node.isBoolean();
        if (type == Byte.class || type == byte.class || type == Short.class || type == short.class
                || type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return node.isIntegralNumber();
        }
        if (Number.class.isAssignableFrom(type) || type == double.class || type == float.class) {
            return node.isNumber();
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type) || Iterable.class.isAssignableFrom(type)) {
            return node.isArray();
        }
        if (Map.class.isAssignableFrom(type)) return node.isObject();
        return node.isObject();
    }

    public record FieldSpec(String name, Class<?> type, boolean required) {
        public FieldSpec {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("field name is required");
            if (type == null) throw new IllegalArgumentException("field type is required");
        }
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}
