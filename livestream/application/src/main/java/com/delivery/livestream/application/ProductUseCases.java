package com.delivery.livestream.application;
import com.delivery.livestream.api.ProductPorts;
import com.delivery.livestream.domain.LivestreamPolicy;
import java.util.List;
import java.util.UUID;
public final class ProductUseCases<R,P,V,C> {
    private final ProductPorts<R,P,V,C> ports;
    public ProductUseCases(ProductPorts<R,P,V,C> ports) { this.ports = ports; }
    private R authorized(UUID id, Long seller, boolean admin) {
        R room = ports.room(id);
        LivestreamPolicy.seller(ports.roomSnapshot(room).sellerId(), seller, admin);
        return room;
    }
    public V pin(UUID id, C command, Long productId, Long restaurantId, Long seller, boolean admin) {
        R room = authorized(id, seller, admin);
        var state = ports.roomSnapshot(room);
        LivestreamPolicy.productScope(state.restaurantId(), restaurantId);
        LivestreamPolicy.productStatus(state.status(), "thêm");
        P product = ports.findOrCreate(id, productId);
        LivestreamPolicy.duplicate(ports.snapshot(product).pinned());
        ports.price(product, command);
        product = ports.pin(product, room, command);
        ports.pinned(id, product, command);
        return ports.response(product);
    }
    public void unpin(UUID id, Long productId, Long seller, boolean admin) {
        R room = authorized(id, seller, admin);
        LivestreamPolicy.productStatus(ports.roomSnapshot(room).status(), "bỏ");
        ports.unpin(ports.find(id, productId));
        ports.unpinned(id, productId);
    }
    public void remove(UUID id, Long productId, Long seller, boolean admin) {
        R room = authorized(id, seller, admin);
        LivestreamPolicy.productStatus(ports.roomSnapshot(room).status(), "xóa");
        ports.remove(ports.find(id, productId), seller);
    }
    public List<V> list(UUID id, boolean pinnedOnly) {
        return ports.list(id, pinnedOnly, 100).stream().map(ports::response).collect(java.util.stream.Collectors.toList());
    }
}
