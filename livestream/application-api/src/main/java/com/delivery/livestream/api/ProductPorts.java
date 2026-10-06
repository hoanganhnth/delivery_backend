package com.delivery.livestream.api;
import java.util.List;
import java.util.UUID;
public interface ProductPorts<R, P, V, C> {
    R room(UUID id);
    RoomSnapshot roomSnapshot(R room);
    P findOrCreate(UUID room, Long product);
    P find(UUID room, Long product);
    ProductSnapshot snapshot(P product);
    void price(P product, C command);
    P pin(P product, R room, C command);
    void unpin(P product);
    void remove(P product, Long legacySeller);
    void pinned(UUID room, P product, C command);
    void unpinned(UUID room, Long product);
    V response(P product);
    List<P> list(UUID room, boolean pinnedOnly, int limit);
}
