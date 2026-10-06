package com.delivery.notification.domain;

import com.delivery.notification.domain.EventIdentity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventIdentityTest {
    static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static Offer offer(UUID event, Long delivery, Long order, String restaurant, String pickup, String address,
            List<SelectedShipper> shippers) { return new Offer(event, delivery, order, restaurant, pickup, address, shippers); }
    static final List<SelectedShipper> SELECTED = List.of(new SelectedShipper(5L, 0.0));
    static Offer valid() { return offer(EVENT, 1L, 2L, " R ", " P ", " D ", SELECTED); }
    static void invalid(Offer offer, String message) {
        assertEquals(message, assertThrows(IllegalArgumentException.class, () -> EventIdentity.validateOffer(offer)).getMessage());
    }
    @Test void statusVocabularyPreservesUnmappedReturnStatuses() {
        for (String status : new String[]{"PENDING","FINDING_SHIPPER","WAIT_SHIPPER_CONFIRM","SHIPPER_NOT_FOUND",
                "ASSIGNED","PICKED_UP","DELIVERING","DELIVERED","RETURNING","RETURNED","CANCELLED"}) {
            assertDoesNotThrow(() -> EventIdentity.validateStatus(EVENT,1L,2L,3L,status));
        }
        for (String status : new String[]{null,""," ","assigned","IN_PROGRESS"}) badStatus(EVENT,1L,2L,3L,status);
        badStatus(null,1L,2L,3L,"ASSIGNED");
        for (Long id : new Long[]{null,0L,-1L}) {
            badStatus(EVENT,id,2L,3L,"ASSIGNED"); badStatus(EVENT,1L,id,3L,"ASSIGNED"); badStatus(EVENT,1L,2L,id,"ASSIGNED");
        }
    }
    void badStatus(UUID event, Long delivery, Long order, Long user, String status) {
        assertEquals("stable eventId, positive delivery/order/user IDs and status are required",
                assertThrows(IllegalArgumentException.class, () -> EventIdentity.validateStatus(event,delivery,order,user,status)).getMessage());
    }
    @Test void offerValidationOrderAndSingleSelection() {
        invalid(null,"Invalid single-shipper offer for delivery: null");
        for (List<SelectedShipper> selected : Arrays.asList(null, List.<SelectedShipper>of(), List.of(SELECTED.get(0),SELECTED.get(0)))) {
            invalid(offer(null,1L,null,null,null,null,selected),"Invalid single-shipper offer for delivery: 1");
        }
        invalid(offer(null,null,null,null,null,null,SELECTED),"Persisted shipper offer is missing eventId");
        for (Long id : new Long[]{null,0L,-1L}) {
            invalid(offer(EVENT,id,2L,null,null,null,SELECTED),"Persisted shipper offer requires positive delivery/order IDs");
            invalid(offer(EVENT,1L,id,null,null,null,SELECTED),"Persisted shipper offer requires positive delivery/order IDs");
        }
        for (String blank : new String[]{null,""," \t"}) {
            invalid(offer(EVENT,1L,2L,blank,"P","D",SELECTED),"Persisted shipper offer requires canonical restaurant and address text");
            invalid(offer(EVENT,1L,2L,"R",blank,"D",SELECTED),"Persisted shipper offer requires canonical restaurant and address text");
            invalid(offer(EVENT,1L,2L,"R","P",blank,SELECTED),"Persisted shipper offer requires canonical restaurant and address text");
        }
        assertDoesNotThrow(() -> EventIdentity.validateOffer(valid()));
    }
    @Test void shipperAndDistanceChecksRetainFiniteNonnegativeContract() {
        for (Long id : new Long[]{null,0L,-1L}) invalidSelection(id,0.0);
        for (Double distance : new Double[]{null,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.1}) invalidSelection(5L,distance);
        for (double distance : new double[]{-0.0,0.0,1.25,Double.MAX_VALUE}) assertDoesNotThrow(() -> EventIdentity.validateOffer(
                offer(EVENT,1L,2L,"R","P","D",List.of(new SelectedShipper(5L,distance)))));
        assertThrows(NullPointerException.class, () -> EventIdentity.validateOffer(
                offer(EVENT,1L,2L,"R","P","D",Collections.singletonList(null))));
    }
    void invalidSelection(Long id, Double distance) {
        invalid(offer(EVENT,1L,2L,"R","P","D",List.of(new SelectedShipper(id,distance))),
                "Persisted shipper offer has invalid shipper/distance identity");
    }
    @Test void blankNamesBecomeNullWithoutTrimmingValidNames() {
        for (String name : new String[]{null,""," \t"}) assertNull(EventIdentity.shipperName(name));
        assertEquals(" A ",EventIdentity.shipperName(" A "));
    }
}
