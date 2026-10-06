package com.delivery.analytics.domain;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReceiptKeyAndPaymentProjectionTest {
    @Test void keyTruthTablePreservesEventTextAndTypeNamespace() {
        assertEquals("ORDER_CREATED:event: id ",ReceiptKey.resolve("ORDER_CREATED",null," id "));
        assertEquals("ORDER_CANCELLED:event:id",ReceiptKey.resolve("ORDER_CANCELLED",-1L,"id"));
        for(String eventId:new String[]{null,"","  "}) {
            assertEquals("TYPE:order:1",ReceiptKey.resolve("TYPE",1L,eventId));
            for(Long id:new Long[]{null,0L,-1L}) assertEquals("Analytics event requires a positive orderId",
                    assertThrows(IllegalArgumentException.class,()->ReceiptKey.resolve("TYPE",id,eventId)).getMessage());
        }
    }
    @Test void paymentCountersRetainAmountsAndUncheckedOverflow() {
        var before=new PaymentProjection(2,3,BigDecimal.TEN);
        assertEquals(new PaymentProjection(3,3,new BigDecimal("9")),before.completed(new BigDecimal("-1")));
        assertEquals(new PaymentProjection(2,4,BigDecimal.TEN),before.failure());
        assertEquals(BigDecimal.TEN,before.completed(BigDecimal.ZERO).amount());
        var overflow=new PaymentProjection(Long.MAX_VALUE,Long.MAX_VALUE,BigDecimal.ZERO);
        assertEquals(Long.MIN_VALUE,overflow.completed(BigDecimal.ZERO).successful());
        assertEquals(Long.MIN_VALUE,overflow.failure().failed());
    }
}
