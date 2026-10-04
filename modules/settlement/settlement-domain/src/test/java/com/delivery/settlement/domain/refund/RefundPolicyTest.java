package com.delivery.settlement.domain.refund;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.delivery.settlement.domain.refund.RefundPolicy.*;

class RefundPolicyTest {
    private Cancellation cancellation() {
        return new Cancellation(UUID.randomUUID(), "ORDER_CANCELLED", 1L, 2L, 20L, 3L,
                "PENDING", "CANCELLED", "cancel", null, "SYSTEM", "SYSTEM_CANCELLED",
                "ONLINE", new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("110"));
    }
    private DeliveryException exception() {
        return new DeliveryException(UUID.randomUUID(), "DELIVERY_EXCEPTION_REPORTED", UUID.randomUUID(),
                4L, 1L, 2L, 20L, 3L, 5L, "PICKED_UP", "PICKED_UP", "RETRY_AVAILABLE", "failed",
                "COD", new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("110"));
    }
    private <T extends Record> T change(T source, String name, Object value) {
        try {
            var components = source.getClass().getRecordComponents();
            var types = new Class<?>[components.length]; var values = new Object[components.length];
            for (int i=0;i<components.length;i++) {
                types[i]=components[i].getType();
                values[i]=components[i].getName().equals(name) ? value : components[i].getAccessor().invoke(source);
            }
            @SuppressWarnings("unchecked") T changed = (T)source.getClass().getDeclaredConstructor(types).newInstance(values);
            return changed;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void invalid(Cancellation value, String message) {
        assertEquals(message, assertThrows(IllegalArgumentException.class, () -> validate(value)).getMessage());
    }
    private void invalid(DeliveryException value, String message) {
        assertEquals(message, assertThrows(IllegalArgumentException.class, () -> validate(value)).getMessage());
    }
    @Test void cancellationIdentityAndTextValidationRetainTheirOrder() {
        String identity="refund cancellation event identity is required";
        invalid((Cancellation)null,identity);
        for (var field : new String[]{"eventId","orderId","userId","restaurantId"}) invalid(change(cancellation(),field,null),identity);
        for (var field : new String[]{"orderId","userId","restaurantId"}) invalid(change(cancellation(),field,0L),identity);
        invalid(change(cancellation(),"eventType","OTHER"),"refund event type is invalid");
        for(var field:new String[]{"previousStatus","cancelReason"}) {
            invalid(change(cancellation(),field,null),"previous status and cancellation reason are required");
            invalid(change(cancellation(),field," "),"previous status and cancellation reason are required");
        }
        invalid(change(cancellation(),"paymentMethod","CARD"),"refund payment method must be COD or ONLINE");
        invalid(change(cancellation(),"currentStatus","PENDING"),"refund cancellation requires CANCELLED status");
    }
    @Test void canonicalMoneyValidationRejectsMissingNegativeZeroAndUnreconciledSnapshots() {
        for(var field:new String[]{"subtotalPrice","discountAmount","shippingFee"}) {
            invalid(change(cancellation(),field,null),field+" must be non-negative");
            invalid(change(cancellation(),field,BigDecimal.ONE.negate()),field+" must be non-negative");
            invalid(change(exception(),field,null),field+" must be non-negative");
            invalid(change(exception(),field,BigDecimal.ONE.negate()),field+" must be non-negative");
        }
        for(BigDecimal value:new BigDecimal[]{null,BigDecimal.ZERO,BigDecimal.ONE.negate()}) {
            invalid(change(cancellation(),"totalPrice",value),"totalPrice must be positive");
            invalid(change(exception(),"totalPrice",value),"totalPrice must be positive");
        }
        invalid(change(cancellation(),"totalPrice",BigDecimal.ONE),"order monetary snapshot does not reconcile");
        invalid(change(exception(),"totalPrice",BigDecimal.ONE),"delivery exception monetary snapshot does not reconcile");
        assertDoesNotThrow(() -> validate(cancellation())); assertDoesNotThrow(() -> validate(exception()));
    }
    @Test void noShipperAndPaymentFailureRequireCanonicalSourceAndStatus() {
        var noShipper=change(cancellation(),"eventType","REFUND_ELIGIBLE");
        invalid(noShipper,"shipper-not-found refund requires SHIPPER_NOT_FOUND status");
        noShipper=change(noShipper,"currentStatus","SHIPPER_NOT_FOUND");
        assertEquals(Trigger.SHIPPER_NOT_FOUND,resolveTrigger(noShipper));
        assertDoesNotThrow(() -> validate(change(cancellation(),"cancelReasonCode","SYSTEM_CANCELLED")));
        invalid(change(noShipper,"cancelledBySource","CUSTOMER"),"shipper-not-found refund must be system sourced");
        assertEquals(Status.REQUESTED,decide(noShipper,true).status());
        var reason=change(change(cancellation(),"cancelReasonCode","SHIPPER_NOT_FOUND"),"currentStatus","SHIPPER_NOT_FOUND");
        assertDoesNotThrow(() -> validate(reason));
        var failed=change(cancellation(),"cancelReasonCode","PAYMENT_FAILED");
        assertEquals(Trigger.PAYMENT_FAILED,resolveTrigger(failed));
        assertEquals(Status.REQUESTED,decide(failed,true).status());
        invalid(change(failed,"paymentMethod","COD"),"payment failure refund must be ONLINE");
        assertEquals(Status.MANUAL_REVIEW,decide(change(failed,"cancelledBySource","CUSTOMER"),true).status());
    }
    @Test void codPrePickupNeedsNoRefundAndOnlineProviderOffNeedsReview() {
        for(var status:new String[]{"PENDING","CONFIRMED","FINDING_SHIPPER","WAIT_SHIPPER_CONFIRM","ASSIGNED"}) {
            var cod=change(change(cancellation(),"previousStatus",status),"paymentMethod","COD");
            var decision=decide(cod,true);
            assertEquals(Status.NO_REFUND_REQUIRED,decision.status());
            assertEquals(BigDecimal.ZERO,decision.capturedAmount()); assertEquals(BigDecimal.ZERO,decision.refundAmount());
        }
        assertEquals(Status.MANUAL_REVIEW,decide(cancellation(),false).status());
        assertEquals(new BigDecimal("110"),decide(cancellation(),false).capturedAmount());
        assertEquals(Status.MANUAL_REVIEW,decide(change(cancellation(),"previousStatus","PICKED_UP"),true).status());
        assertEquals(BigDecimal.ZERO,decide(change(change(cancellation(),"previousStatus","PICKED_UP"),"paymentMethod","COD"),true).refundAmount());
    }
    @Test void automaticCancellationAdmissionPreservesActorAndReasonPolicy() {
        var customer=change(change(cancellation(),"cancelledBySource"," customer "),"cancelReasonCode","CUSTOMER_CANCELLED");
        assertEquals("CUSTOMER",actorSource(customer));
        assertEquals(Status.REQUESTED,decide(customer,true).status());
        assertEquals(Status.MANUAL_REVIEW,decide(change(customer,"previousStatus","CONFIRMED"),true).status());
        assertEquals(Status.MANUAL_REVIEW,decide(change(customer,"cancelReasonCode","OTHER"),true).status());
        var restaurant=change(change(cancellation(),"cancelledBySource","RESTAURANT"),"cancelReasonCode","RESTAURANT_REJECTED");
        assertEquals(Status.REQUESTED,decide(restaurant,true).status());
        assertEquals(Status.MANUAL_REVIEW,decide(change(restaurant,"cancelReasonCode","OTHER"),true).status());
        assertEquals(Status.MANUAL_REVIEW,decide(change(cancellation(),"cancelReasonCode","OTHER"),true).status());
        var legacy=change(change(cancellation(),"cancelledBySource",null),"cancelledBy",99L);
        assertEquals("LEGACY_ACTOR",actorSource(legacy));
        assertEquals(Status.MANUAL_REVIEW,decide(legacy,true).status());
        assertEquals("SYSTEM",actorSource(change(cancellation(),"cancelledBySource"," ")));
        assertEquals(Status.MANUAL_REVIEW,decideStatus(cancellation(),Trigger.DELIVERY_DISPUTE,true));
    }
    @Test void deliveryExceptionIdentityAndEventValidationAreComplete() {
        String identity="delivery exception refund identity is required";
        invalid((DeliveryException)null,identity);
        for(var field:new String[]{"eventId","exceptionId","deliveryId","orderId","userId","restaurantId","shipperId"}) invalid(change(exception(),field,null),identity);
        for(var field:new String[]{"deliveryId","orderId","userId","restaurantId","shipperId"}) invalid(change(exception(),field,0L),identity);
        invalid(change(exception(),"eventType","OTHER"),"delivery exception refund event type is invalid");
        invalid(change(exception(),"exceptionStatus","RETURNING"),"delivery exception refund event type is invalid");
        for(var field:new String[]{"previousDeliveryStatus","currentDeliveryStatus","reason"})
            invalid(change(exception(),field,null),"delivery exception must preserve one post-pickup status and a reason");
        invalid(change(exception(),"reason"," "),"delivery exception must preserve one post-pickup status and a reason");
        invalid(change(exception(),"previousDeliveryStatus","ASSIGNED"),"delivery exception must preserve one post-pickup status and a reason");
        invalid(change(exception(),"paymentMethod","CARD"),"delivery exception payment method must be COD or ONLINE");
    }
    @Test void postPickupDisputesAlwaysRemainManualAndUseOnlyOnlineCapturedMoney() {
        for(var status:new String[]{"PICKED_UP","DELIVERING"}) {
            var value=change(change(exception(),"previousDeliveryStatus",status),"currentDeliveryStatus",status);
            assertDoesNotThrow(() -> validate(value));
            var cod=decide(value); assertEquals(Trigger.DELIVERY_DISPUTE,cod.trigger());
            assertEquals(Status.MANUAL_REVIEW,cod.status());assertEquals("SHIPPER",cod.actorSource());
            assertEquals(BigDecimal.ZERO,cod.refundAmount());
            var online=decide(change(value,"paymentMethod","ONLINE"));
            assertEquals(new BigDecimal("110"),online.refundAmount());assertEquals(online.capturedAmount(),online.refundAmount());
        }
    }
}
