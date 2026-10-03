package com.delivery.tracking_service.service;

import com.delivery.tracking.application.DefaultTrackingService;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.Coordinate;
import com.delivery.tracking.domain.LocationUpdateSource;
import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationPublicationMappingTest {
    @Test void cacheKafkaAndFanoutKeepEveryFieldAndTheExistingTimestampEncodingForEachSource() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        for (var source : LocationUpdateSource.values()) {
            var cache = mock(ShipperLocationRepository.class); var kafka = mock(ShipperLocationEventPublisher.class);
            var fanout = mock(LocationFanoutPublisher.class); var adapter = new RedisLocationUpdateAdapter(cache, kafka, fanout);
            var core = new DefaultTrackingService(adapter, adapter, Clock.fixed(now, ZoneOffset.UTC));
            core.updateLocation(new UpdateLocationCommand(7, new Coordinate(10.77, 106.7), null, 12.0, 90.0, true, source));
            var saved = ArgumentCaptor.forClass(ShipperLocationResponse.class);
            var event = ArgumentCaptor.forClass(ShipperLocationResponse.class);
            var broadcast = ArgumentCaptor.forClass(ShipperLocationResponse.class);
            verify(cache).cacheShipperLocation(eq(7L), saved.capture());
            verify(kafka).publishLocationUpdate(event.capture(), eq(source.name())); verify(fanout).publish(broadcast.capture());
            var result = saved.getValue();
            assertThat(event.getValue()).usingRecursiveComparison().isEqualTo(result);
            assertThat(broadcast.getValue()).usingRecursiveComparison().isEqualTo(result);
            assertThat(result.getShipperId()).isEqualTo(7L); assertThat(result.getLatitude()).isEqualTo(10.77);
            assertThat(result.getLongitude()).isEqualTo(106.7); assertThat(result.getAccuracy()).isNull();
            assertThat(result.getSpeed()).isEqualTo(12.0); assertThat(result.getHeading()).isEqualTo(90.0);
            assertThat(result.getIsOnline()).isTrue(); assertThat(result.getDistance()).isNull();
            String time = source == LocationUpdateSource.APPLICATION ? now.toString() : LocalDateTime.ofInstant(now, ZoneId.systemDefault()).toString();
            assertThat(result.getLastPing()).isEqualTo(time); assertThat(result.getUpdatedAt()).isEqualTo(time);
        }
    }
    @Test void onlineFlagAcceptsOnlyBooleanLiteralsAndRetainsAbsentTrueDefault() throws Exception {
        var mapper = new ObjectMapper();
        assertThat(mapper.readValue("{}", UpdateLocationRequest.class).getIsOnline()).isTrue();
        assertThat(mapper.readValue("{\"isOnline\":true}", UpdateLocationRequest.class).getIsOnline()).isTrue();
        assertThat(mapper.readValue("{\"isOnline\":false}", UpdateLocationRequest.class).getIsOnline()).isFalse();
        for (String invalid : new String[]{"null", "1", "0", "\"true\"", "\"false\"", "[]", "{}"}) {
            assertThat(mapper.readValue("{\"isOnline\":" + invalid + "}", UpdateLocationRequest.class).getIsOnline()).isNull();
        }
    }
}
