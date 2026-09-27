package com.delivery.platform.kafka;

/** Canonical topic names currently shared by Delivery services. */
public final class TopicNames {

    private TopicNames() {
    }

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_STATUS_UPDATED = "order.status-updated";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_REFUND_ELIGIBLE = "order.refund-eligible";

    public static final String RESTAURANT_ORDER_CONFIRMED = "restaurant.order-confirmed";
    public static final String RESTAURANT_ORDER_REJECTED = "restaurant.order-rejected";

    public static final String DELIVERY_CREATED_RESULT = "delivery.created.result";
    public static final String DELIVERY_CREATED_FAILED = "delivery.created.failed";
    public static final String DELIVERY_CANCEL_FAILED = "delivery.cancel.failed";
    public static final String DELIVERY_STATUS_UPDATED = "delivery.status-updated";
    public static final String DELIVERY_COMPLETED = "delivery.completed";
    public static final String DELIVERY_SHIPPER_ACCEPTED = "delivery.shipper-accepted";
    public static final String DELIVERY_SHIPPER_REJECTED = "delivery.shipper-rejected";
    public static final String DELIVERY_SHIPPER_OFFERED = "delivery.shipper-offered";
    public static final String DELIVERY_OFFER_PERSISTED = "delivery.offer-persisted";
    public static final String DELIVERY_OFFER_RETIRED = "delivery.offer-retired";
    public static final String DELIVERY_EXCEPTION_REPORTED = "delivery.exception.reported";
    public static final String DELIVERY_BATCH_ACCEPTED = "delivery.batch.accepted";
    public static final String DELIVERY_BATCH_RELEASED = "delivery.batch.released";
    public static final String DELIVERY_BATCH_COMPLETED = "delivery.batch.completed";

    public static final String SHIPPER_FOUND = "shipper.found";
    public static final String SHIPPER_NOT_FOUND = "shipper.not-found";
    public static final String SHIPPER_LOCATION_UPDATED = "shipper.location-updated";
    public static final String SHIPPER_STATUS_CHANGE = "shipper.status-change";
    public static final String SHIPPER_IDENTITY_UPSERTED = "shipper.identity.upserted";

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    public static final String SAGA_COMMAND_CREATE_DELIVERY = "saga.command.create-delivery";
    public static final String SAGA_COMMAND_CANCEL_DELIVERY = "saga.command.cancel-delivery";
    public static final String SAGA_COMMAND_FIND_SHIPPER = "saga.command.find-shipper";
    public static final String SAGA_COMMAND_CACHE_SHIPPER_FOUND = "saga.command.cache-shipper-found";
    public static final String SAGA_COMMAND_EXPIRE_SHIPPER_OFFER = "saga.command.expire-shipper-offer";
    public static final String SAGA_COMMAND_MARK_SHIPPER_NOT_FOUND = "saga.command.mark-shipper-not-found";
    public static final String SAGA_COMMAND_STOP_MATCHING = "saga.command.stop-matching";
    public static final String SAGA_COMMAND_UPDATE_ORDER_STATUS = "saga.command.update-order-status";

    public static final String MATCHING_DECISION_TRACE = "matching.decision-trace";
    public static final String ENTITY_SYNC = "entity-sync";
    public static final String VOUCHER_RESERVATION_EVENTS = "voucher.reservation.events";
    public static final String FLASH_SALE_RESERVATION_EVENTS = "flash-sale.reservation.events";
    public static final String REFUND_REQUESTED = "refund.requested";
    public static final String IDENTITY_PROFILE_CREATED = "identity.profile.created";
    public static final String IDENTITY_STATUS_CHANGED = "identity.status.changed";

    public static final String LIVESTREAM_STARTED = "livestream.started";
    public static final String LIVESTREAM_ENDED = "livestream.ended";
    public static final String LIVESTREAM_PRODUCT_PINNED = "livestream.product.pinned";
    public static final String LIVESTREAM_PRODUCT_UNPINNED = "livestream.product.unpinned";

    public static final String DLT_SUFFIX = ".DLT";

    public static String deadLetterTopic(String sourceTopic) {
        return sourceTopic + DLT_SUFFIX;
    }
}
