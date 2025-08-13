
package com.delivery.tracking_service.grpc;

import com.delivery.tracking_service.repository.RedisGeoRepository;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ShipperLocationGrpcService extends ShipperLocationServiceGrpc.ShipperLocationServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(ShipperLocationGrpcService.class);
    private final RedisGeoRepository redisGeoRepository;
    private final ShipperLocationWebSocketHandler webSocketHandler;

    public ShipperLocationGrpcService(RedisGeoRepository redisGeoRepository, 
                                     ShipperLocationWebSocketHandler webSocketHandler) {
        this.redisGeoRepository = redisGeoRepository;
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public StreamObserver<ShipperLocation> streamLocation(
            StreamObserver<LocationAck> responseObserver) {
        return new StreamObserver<ShipperLocation>() {
            @Override
            public void onNext(ShipperLocation location) {
                // Lưu vị trí vào Redis
                ShipperLocationResponse response = new ShipperLocationResponse();
                response.setShipperId(location.getShipperId());
                response.setLatitude(location.getLatitude());
                response.setLongitude(location.getLongitude());
                response.setAccuracy(location.getAccuracy());
                response.setSpeed(location.getSpeed());
                response.setHeading(location.getHeading());
                response.setIsOnline(location.getIsOnline());
                response.setLastPing(String.valueOf(location.getTimestamp()));
                response.setUpdatedAt(String.valueOf(location.getTimestamp()));
                redisGeoRepository.cacheShipperLocation(location.getShipperId(), response);
                
                // ✅ Broadcast vị trí mới qua WebSocket
                webSocketHandler.broadcastShipperLocation(response);
                webSocketHandler.broadcastAreaLocationUpdate(response);
                
                log.info("[gRPC] Updated location for shipper {}: ({}, {}) + WebSocket broadcast",
                        location.getShipperId(), location.getLatitude(), location.getLongitude());
                // Gửi ack về client
                LocationAck ack = LocationAck.newBuilder()
                        .setMessage("Location received for shipperId: " + location.getShipperId())
                        .setLocation(location)
                        .build();
                responseObserver.onNext(ack);
            }

            @Override
            public void onError(Throwable t) {
                log.error("[gRPC] Error in streamLocation: {}", t.getMessage(), t);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
