package com.delivery.livestream.api;
import java.util.List;
import java.util.UUID;
/** Host-owned handles and mapped results never become application dependencies. */
public interface LifecyclePorts<R, V, T, S, J, C> {
    R find(UUID id);
    RoomSnapshot snapshot(R room);
    R create(C command, Long seller);
    R start(R room);
    R end(R room);
    R count(R room, long count);
    T token(UUID id, Long user, String role, int ttl);
    void started(R room);
    void ended(R room);
    V response(R room);
    S startResponse(R room, T token, int uid);
    J joinResponse(R room, T token, int uid);
    List<R> list(String filter, Long value, int limit);
}
