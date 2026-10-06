package com.delivery.livestream.application;
import com.delivery.livestream.api.CheckoutCommand;
import com.delivery.livestream.api.CheckoutPorts;
import com.delivery.livestream.domain.CheckoutValidationPolicy;
import com.delivery.livestream.domain.CheckoutFingerprint;
import com.delivery.livestream.domain.LivestreamPolicy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
public final class CheckoutUseCases<R,P,E,I,C,Q> {
    private final CheckoutPorts<R,P,E,I,C,Q> ports;
    public CheckoutUseCases(CheckoutPorts<R,P,E,I,C,Q> ports) { this.ports = ports; }
    private void validate(CheckoutCommand command) {
        if (command == null) throw new IllegalArgumentException("Invalid livestream checkout quote scope");
        CheckoutValidationPolicy.requireScope(command.room(), command.restaurant(), command.products());
    }
    private R room(CheckoutCommand command, boolean context) {
        R room = ports.room(command.room());
        var state = ports.roomSnapshot(room);
        LivestreamPolicy.checkoutRoom(state.status(), state.restaurantId(), command.restaurant(), context);
        return room;
    }
    private Map<Long,P> pins(CheckoutCommand command) {
        return ports.pins(command.room(), command.products()).stream()
            .collect(Collectors.toMap(p -> ports.snapshot(p).productId(), Function.identity()));
    }
    public Q quote(CheckoutCommand command) {
        validate(command);
        room(command, false);
        var pinned = pins(command);
        List<I> items = command.products().stream().map(pinned::get).filter(java.util.Objects::nonNull).map(p -> {
            var state = ports.snapshot(p);
            CheckoutValidationPolicy.requireQuoteProduct(command.restaurant(), state.restaurantId(), state.price());
            return ports.item(p);
        }).toList();
        return ports.quote(command.room(), command.restaurant(), items);
    }
    public List<C> context(CheckoutCommand command, Long actor, String correlation, String key) {
        CheckoutValidationPolicy.requireContextMetadata(actor, correlation, key);
        validate(command);
        String fingerprint = CheckoutFingerprint.of(command.room(), command.restaurant(), command.products(), actor);
        var existing = ports.receipt(actor, key);
        if (existing.isPresent()) {
            LivestreamPolicy.replay(fingerprint, ports.fingerprint(existing.get()));
            return ports.restore(existing.get());
        }
        R room = room(command, true);
        var pinned = pins(command);
        LivestreamPolicy.completePins(pinned.size(), command.products().size());
        List<C> result = command.products().stream().map(pinned::get).filter(java.util.Objects::nonNull).map(p -> {
            var state = ports.snapshot(p);
            CheckoutValidationPolicy.requireContextProduct(state.id(), state.price(), command.restaurant(), state.restaurantId());
            return ports.context(room, p, actor, correlation, key);
        }).toList();
        return ports.store(actor, key, fingerprint, result);
    }
}
