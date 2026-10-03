package com.delivery.tracking.application.api;
public interface LocationFanoutEventPort {
    void publish(Long deliveryId, FanoutLocation location) throws Exception;
    void failed(Long shipperId, Exception error);
}
