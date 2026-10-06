package com.delivery.promotion.application;

import com.delivery.promotion.application.api.OrderReservationEventPort;
import com.delivery.promotion.application.api.PromotionCommands;
import com.delivery.promotion.domain.OrderReservationEventPolicy;

public final class ProcessOrderReservationEventUseCase {
    public void process(PromotionCommands.OrderEvent command, OrderReservationEventPort port) {
        if (!port.claim(command)) {
            var stored = port.existing(command.eventId());
            String failure = OrderReservationEventPolicy.replayFailure(stored,
                    new OrderReservationEventPolicy.Receipt(command.source(), command.action(), command.orderId(),
                            command.legacyReservationId(), command.fingerprint()));
            if (failure != null) throw new IllegalArgumentException(failure);
            return;
        }
        var operation = OrderReservationEventPolicy.operation(command.action(), command.legacyReservationId(),
                command.bulkReservationId(), command.previousStatus());
        switch (operation) {
            case NONE -> { }
            case COMMIT_BULK -> commit(command, port, true);
            case COMMIT_LEGACY -> commit(command, port, false);
            case RELEASE_BULK -> port.release(command.bulkReservationId(), command.orderId(), true);
            case RELEASE_LEGACY -> port.release(command.legacyReservationId(), command.orderId(), false);
        }
    }
    private void commit(PromotionCommands.OrderEvent command, OrderReservationEventPort port, boolean bulk) {
        String state = port.commit(bulk ? command.bulkReservationId() : command.legacyReservationId(), command.orderId(), bulk);
        String failure = OrderReservationEventPolicy.commitFailure(state, bulk);
        if (failure != null) throw port.conflict(failure);
    }
}
