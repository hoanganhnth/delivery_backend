package com.delivery.analytics.domain;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class SnapshotDecisionsTest {
    @Test void boundsAndTimestampPrecedence() {
        assertEquals(Long.MAX_VALUE,SnapshotDecisions.positive(Long.MAX_VALUE,"positive"));
        for(long n:new long[]{0,-1,Long.MIN_VALUE}) assertEquals("positive",assertThrows(IllegalArgumentException.class,
                ()->SnapshotDecisions.positive(n,"positive")).getMessage());
        SnapshotDecisions.requireSize(0);SnapshotDecisions.requireSize(100);
        assertThrows(IllegalArgumentException.class,()->SnapshotDecisions.requireSize(101));
        assertNull(SnapshotDecisions.firstTimestamp(null,"", "   "));
        assertEquals("first",SnapshotDecisions.firstTimestamp(null," ","first","second"));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.001","1.000"})
    void invalidPrice(String price) {assertThrows(IllegalArgumentException.class,()->SnapshotDecisions.price(new BigDecimal(price)));}
    @Test void itemValidationAndDeltaTruthTable() {
        var price=SnapshotDecisions.price(new BigDecimal("10"));assertEquals(new BigDecimal("10.00"),price);
        assertEquals(new BigDecimal("1.23"),SnapshotDecisions.price(new BigDecimal("1.23")));
        var item=SnapshotDecisions.item(1,2,price,null," Shop ");
        assertEquals("Shop",item.menuItemName());assertEquals(1,item.menuItemId());assertEquals(2,item.quantity());
        assertEquals(new BigDecimal("20.00"),item.lineTotal());
        var ordered=item.delta(false);var cancelled=item.delta(true);
        assertEquals(2,ordered.orderedQuantity());assertEquals(0,ordered.cancelledQuantity());
        assertEquals(item.lineTotal(),ordered.orderedRevenue());assertEquals(BigDecimal.ZERO,ordered.cancelledRevenue());
        assertEquals(0,cancelled.orderedQuantity());assertEquals(2,cancelled.cancelledQuantity());
        assertEquals(BigDecimal.ZERO,cancelled.orderedRevenue());assertEquals(item.lineTotal(),cancelled.cancelledRevenue());
        assertEquals("UNKNOWN",SnapshotDecisions.item(1,2,price,new BigDecimal("20"),"  ").menuItemName());
        assertThrows(IllegalArgumentException.class,()->SnapshotDecisions.item(1,2,price,BigDecimal.ONE,"x"));
    }
}
