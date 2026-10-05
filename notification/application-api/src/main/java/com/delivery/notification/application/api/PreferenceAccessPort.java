package com.delivery.notification.application.api;

/** Public capability facade; canonical actor authorization remains at HTTP boundary. */
public interface PreferenceAccessPort<R> {
    R get(Long principalId);
    R update(Long principalId, boolean enabled);
}
