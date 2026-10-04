package com.delivery.tracking_service.websocket;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketFanoutBenchmarkTest {

    @Test
    void measuresLegacyGlobalDisconnectAndDeliveryRoomFanout() throws IOException {
        int roomsCount = 50_000;
        Map<Long, Set<String>> legacy = new HashMap<>();
        DeliveryRoomRegistry rooms = new DeliveryRoomRegistry();
        for (int i = 1; i <= roomsCount; i++) {
            legacy.computeIfAbsent(100_000L + i, ignored -> new HashSet<>()).add("session-" + i);
            rooms.subscribe(i, 100_000L + i, "session-" + i);
        }

        long legacyStarted = System.nanoTime();
        legacy.values().forEach(subscribers -> subscribers.remove("target-old"));
        long legacyDisconnectNanos = System.nanoTime() - legacyStarted;

        rooms.subscribe(60_000L, 42L, "target-old");
        long roomStarted = System.nanoTime();
        rooms.removeSession("target-old");
        long roomDisconnectNanos = System.nanoTime() - roomStarted;

        Set<String> legacyReusedShipperAudience = new HashSet<>();
        legacyReusedShipperAudience.add("old-delivery-customer");
        legacyReusedShipperAudience.add("new-delivery-customer");
        rooms.subscribe(70_000L, 42L, "old-delivery-customer");
        rooms.activate(70_001L, 42L);
        rooms.subscribe(70_001L, 42L, "new-delivery-customer");

        int legacyFanout = legacyReusedShipperAudience.size();
        int roomFanout = rooms.subscribersForShipper(42L).size();
        assertThat(legacyFanout).isEqualTo(2);
        assertThat(roomFanout).isEqualTo(1);

        Path output = Path.of("target", "phase4-websocket-fanout", "summary.tsv");
        Files.createDirectories(output.getParent());
        Files.writeString(output,
                "rooms\tlegacy_disconnect_ns\troom_disconnect_ns\tlegacy_reused_shipper_fanout\troom_fanout\n"
                + roomsCount + "\t" + legacyDisconnectNanos + "\t" + roomDisconnectNanos
                + "\t" + legacyFanout + "\t" + roomFanout + "\n");
    }
}
