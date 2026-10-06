package com.delivery.livestream.api;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CheckoutPorts<R, P, E, I, C, Q> {
    R room(UUID id);
    RoomSnapshot roomSnapshot(R room);
    List<P> pins(UUID room, List<Long> products);
    ProductSnapshot snapshot(P product);
    Optional<E> receipt(Long actor, String key);
    String fingerprint(E receipt);
    List<C> restore(E receipt);
    /** Host retains REQUIRES_NEW writer and duplicate-insert recovery. */
    List<C> store(Long actor, String key, String fingerprint, List<C> contexts);
    I item(P product);
    C context(R room, P product, Long actor, String correlation, String key);
    Q quote(UUID room, Long restaurant, List<I> items);
}
