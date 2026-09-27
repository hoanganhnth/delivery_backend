package com.delivery.eventtestsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Assertions for wire-level JSON serialization compatibility. */
public final class JsonRoundTripAssert {

    private JsonRoundTripAssert() {
    }

    public static <T> T assertRoundTrip(ObjectMapper mapper, T event, Class<T> eventType) {
        requireArguments(mapper, event, eventType);
        return roundTrip(mapper, event, eventType);
    }

    public static <T> T assertRoundTrip(T event, Class<T> eventType, ObjectMapper mapper) {
        return assertRoundTrip(mapper, event, eventType);
    }

    public static <T> T assertRoundTrip(ObjectMapper mapper, T event, TypeReference<T> eventType) {
        if (mapper == null || event == null || eventType == null) {
            throw new IllegalArgumentException("mapper, event and eventType are required");
        }
        return roundTrip(mapper, event, eventType);
    }

    public static <T> T assertIdentity(ObjectMapper mapper, T event, Class<T> eventType) {
        return assertRoundTrip(mapper, event, eventType);
    }

    public static String json(ObjectMapper mapper, Object event) {
        if (mapper == null || event == null) {
            throw new IllegalArgumentException("mapper and event are required");
        }
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Event could not be serialized", exception);
        }
    }

    private static <T> T roundTrip(ObjectMapper mapper, T event, Class<T> eventType) {
        return compareJson(mapper, event, () -> mapper.readValue(json(mapper, event), eventType));
    }

    private static <T> T roundTrip(ObjectMapper mapper, T event, TypeReference<T> eventType) {
        return compareJson(mapper, event, () -> mapper.readValue(json(mapper, event), eventType));
    }

    private static <T> T compareJson(ObjectMapper mapper, T event, Deserializer<T> deserializer) {
        String originalJson = json(mapper, event);
        try {
            T restored = deserializer.read();
            JsonNode original = mapper.readTree(originalJson);
            JsonNode roundTripped = mapper.readTree(mapper.writeValueAsString(restored));
            if (!original.equals(roundTripped)) {
                throw new AssertionError("Event JSON changed during round trip\noriginal: "
                        + original + "\nrestored: " + roundTripped);
            }
            return restored;
        } catch (AssertionError assertion) {
            throw assertion;
        } catch (Exception exception) {
            throw new AssertionError("Event failed JSON round trip: " + event.getClass().getName(), exception);
        }
    }

    private static void requireArguments(ObjectMapper mapper, Object event, Object eventType) {
        if (mapper == null || event == null || eventType == null) {
            throw new IllegalArgumentException("mapper, event and eventType are required");
        }
    }

    @FunctionalInterface
    private interface Deserializer<T> {
        T read() throws Exception;
    }
}
