package com.delivery.analytics.domain;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderProjectionTest {
    private OrderProjection state(long delivered,long pending) {return new OrderProjection(10,delivered,2,pending,new BigDecimal("11"),BigDecimal.TEN);}
    @Test void createdOnlyIncrementsTotalAndPending() {
        assertEquals(new OrderProjection(11,1,2,4,new BigDecimal("11"),BigDecimal.TEN),state(1,3).created());
    }
    @Test void pendingClampsAtZeroAndRetainsPreExistingNegativeValues() {
        for(long pending:new long[]{-2,0,3}) {
            var result=state(1,pending).delivered(null);
            assertEquals(pending>0?pending-1:pending,result.pending());assertEquals(2,result.delivered());
            assertEquals(new BigDecimal("6"),result.average());assertEquals(new BigDecimal("11"),result.revenue());
            var cancelled=state(1,pending).cancel();
            assertEquals(pending>0?pending-1:pending,cancelled.pending());assertEquals(3,cancelled.cancelled());
            assertEquals(10,cancelled.total());
        }
    }
    @Test void negativeAmountAndCounterOverflowRemainUnchanged() {
        assertEquals(new BigDecimal("-5"),state(1,0).delivered(new BigDecimal("-20")).average());
        assertEquals(BigDecimal.TEN,state(Long.MAX_VALUE,0).delivered(BigDecimal.ZERO).average());
        assertEquals(Long.MIN_VALUE,state(Long.MAX_VALUE,0).delivered(BigDecimal.ZERO).delivered());
        var overflow=new OrderProjection(Long.MAX_VALUE,0,Long.MAX_VALUE,Long.MAX_VALUE,BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals(Long.MIN_VALUE,overflow.created().total());assertEquals(Long.MIN_VALUE,overflow.created().pending());
        assertEquals(Long.MIN_VALUE,overflow.cancel().cancelled());
    }
}
