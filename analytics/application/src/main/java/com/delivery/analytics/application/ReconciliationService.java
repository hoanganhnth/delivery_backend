package com.delivery.analytics.application;
import com.delivery.analytics.applicationapi.*;
import com.delivery.analytics.domain.OrderReconciliationAccumulator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
public final class ReconciliationService implements ReconciliationUseCase {
    private static final int PAGE_SIZE=500;
    private final ReconciliationPort port;
    public ReconciliationService(ReconciliationPort port) {this.port=port;}
    public Result reconcile(LocalDate date) {
        var platform=new OrderReconciliationAccumulator();
        Map<Long,OrderReconciliationAccumulator> restaurants=new HashMap<>();
        long processed=0;
        for(int pageNumber=0;;pageNumber++) {
            var page=port.receipts(date,pageNumber,PAGE_SIZE);
            for(var event:page.content()) {
                platform.accept(event.eventType(),event.amount());
                if(event.restaurantId()!=null) restaurants.computeIfAbsent(event.restaurantId(),
                        ignored -> new OrderReconciliationAccumulator()).accept(event.eventType(),event.amount());
                processed++;
            }
            if(!page.hasNext()) break;
        }
        var zero=new OrderReconciliationAccumulator().snapshot();
        for(int pageNumber=0;;pageNumber++) {
            var page=port.scopes(date,pageNumber,PAGE_SIZE);
            for(var scope:page.content()) {
                boolean observed=scope.restaurantId()==null?processed>0:restaurants.containsKey(scope.restaurantId());
                if(!observed) scope.overwrite(zero);
            }
            if(!page.hasNext()) break;
        }
        if(processed==0) return new Result(0,zero,0);
        port.overwrite(date,null,platform.snapshot());
        restaurants.forEach((id, accumulator) -> port.overwrite(date,id,accumulator.snapshot()));
        return new Result(processed,platform.snapshot(),restaurants.size());
    }
}
