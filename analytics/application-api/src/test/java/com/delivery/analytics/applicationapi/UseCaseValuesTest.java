package com.delivery.analytics.applicationapi;
import com.delivery.analytics.domain.*;
import com.delivery.analytics.domain.DashboardValues.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UseCaseValuesTest {
    @Test void typedValuesRetainNullableScopesIdentityAndOrderedPages() {
        var identity=new ReceiptIdentity("ORDER_CREATED",1L,null,7L,"Shop",BigDecimal.TEN,"PENDING",null,null,"raw","hash");
        var command=new IngestionUseCase.Command("key",identity);
        assertEquals("key",command.deduplicationKey());assertSame(identity,command.identity());
        var scope=new DashboardReadPort.Scope(false,null);
        assertFalse(scope.platform());assertNull(scope.restaurantId());
        var receipt=new ReconciliationPort.Receipt("ORDER_DELIVERED",7L,BigDecimal.TEN);
        assertEquals("ORDER_DELIVERED",receipt.eventType());assertEquals(7L,receipt.restaurantId());assertEquals(BigDecimal.TEN,receipt.amount());
        var page=new ReconciliationPort.Page<>(List.of(receipt),true);
        assertEquals(List.of(receipt),page.content());assertTrue(page.hasNext());
        var result=new ReconciliationUseCase.Result(2,new OrderReconciliationAccumulator().snapshot(),1);
        assertEquals(2,result.processed());assertEquals(1,result.restaurants());assertEquals(0,result.platform().created());
        var overview=DashboardValues.overview(null);var points=List.of(new Point("T1",0,BigDecimal.ZERO));
        var statuses=List.of(new Status("PENDING",0));var top=List.of(new TopRestaurant(7L,"Shop",0,BigDecimal.ZERO));
        var dashboard=new DashboardUseCase.Dashboard(overview,points,statuses,top);
        assertSame(overview,dashboard.overview());assertSame(points,dashboard.series());
        assertSame(statuses,dashboard.statuses());assertSame(top,dashboard.topRestaurants());
    }
}
