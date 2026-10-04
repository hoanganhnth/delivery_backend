package com.delivery.tracking.application.api;
import java.util.Optional;
import java.util.Set;
public interface FanoutDeliveryReadPort {
    Set<Long> activeDeliveries(Long shipperId);
    Optional<Long> activeDelivery(Long shipperId);
}
