package com.delivery.analytics.application;
import com.delivery.analytics.applicationapi.ReconciliationPort;
import com.delivery.analytics.domain.OrderReconciliationAccumulator.Snapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReconciliationServiceTest {
    private static final LocalDate DATE=LocalDate.of(2026,10,6);
    private final List<String> calls=new ArrayList<>();
    private final Map<Long,Snapshot> written=new HashMap<>();
    private List<List<ReconciliationPort.Receipt>> receipts=List.of(List.of());
    private List<List<ReconciliationPort.Scope>> scopes=List.of(List.of());
    private String failure;
    private final RuntimeException error=new IllegalStateException("storage failed");
    private void call(String action) {calls.add(action);if(action.equals(failure)) throw error;}
    private ReconciliationPort.Scope scope(Long id) {return new ReconciliationPort.Scope() {
        public Long restaurantId() {return id;}
        public void overwrite(Snapshot value) {call("reset:"+id);written.put(id,value);}
    };}
    private final ReconciliationService service=new ReconciliationService(new ReconciliationPort() {
        public Page<Receipt> receipts(LocalDate date,int page,int size) {
            assertEquals(DATE,date);assertEquals(500,size);call("receipts:"+page);
            return new Page<>(receipts.get(page),page+1<receipts.size());
        }
        public Page<Scope> scopes(LocalDate date,int page,int size) {
            assertEquals(DATE,date);assertEquals(500,size);call("scopes:"+page);
            return new Page<>(scopes.get(page),page+1<scopes.size());
        }
        public void overwrite(LocalDate date,Long id,Snapshot snapshot) {assertEquals(DATE,date);call("write:"+id);written.put(id,snapshot);}
    });
    private ReconciliationPort.Receipt event(String type,Long id,BigDecimal amount) {return new ReconciliationPort.Receipt(type,id,amount);}
    @Test void pagesBothSourcesResetsStaleScopesThenOverwritesObservedScopes() {
        receipts=List.of(List.of(event("ORDER_CREATED",7L,null),event("ORDER_DELIVERED",7L,new BigDecimal("11"))),
                List.of(event("ORDER_DELIVERED",7L,null),event("ORDER_CANCELLED",null,null)));
        scopes=List.of(List.of(scope(null),scope(7L)),List.of(scope(8L)));
        var result=service.reconcile(DATE);
        assertEquals(4,result.processed());assertEquals(1,result.restaurants());assertEquals(1,result.platform().created());
        assertEquals(List.of("receipts:0","receipts:1","scopes:0","scopes:1","reset:8","write:null","write:7"),calls);
        assertEquals(1,written.get(null).created());assertEquals(2,written.get(null).delivered());assertEquals(1,written.get(null).cancelled());
        assertEquals(0,written.get(null).pending());assertEquals(new BigDecimal("6"),written.get(null).averageOrderValue());
        assertEquals(0,written.get(8L).created());assertEquals(BigDecimal.ZERO,written.get(8L).revenue());
        assertEquals(0,written.get(7L).cancelled());
    }
    @Test void emptyDayResetsExistingRowsWithoutCreatingAnyNewScope() {
        scopes=List.of(List.of(scope(null),scope(8L)));service.reconcile(DATE);
        assertEquals(List.of("receipts:0","scopes:0","reset:null","reset:8"),calls);
        assertEquals(2,written.size());assertEquals(0,written.get(null).created());
    }
    @Test void nonOrderReceiptStillCreatesZeroPlatformAndRestaurantScopeAsBefore() {
        receipts=List.of(List.of(event("PAYMENT_COMPLETED",7L,BigDecimal.TEN)));
        service.reconcile(DATE);
        assertEquals(Set.of(7L),written.keySet().stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        assertTrue(written.containsKey(null));assertEquals(BigDecimal.ZERO,written.get(7L).revenue());
    }
    @Test void emptyDayWithoutRowsDoesNotWrite() {service.reconcile(DATE);assertTrue(written.isEmpty());}
    @Test void platformOnlyDoesNotCreateRestaurantScopes() {
        receipts=List.of(List.of(event("ORDER_CREATED",null,null)));service.reconcile(DATE);
        assertEquals(1,written.size());assertEquals(1,written.get(null).created());
    }
    @Test void nullReceiptTypeStillFailsBeforeResetOrOverwrite() {
        receipts=List.of(List.of(event(null,7L,null)));
        assertThrows(NullPointerException.class,()->service.reconcile(DATE));assertEquals(List.of("receipts:0"),calls);
    }
    @Test void storageFailuresPropagateFromReadResetAndOverwrite() {
        for(String stage:List.of("receipts:0","scopes:0","reset:8","write:null","write:7")) {
            calls.clear();written.clear();failure=stage;
            receipts=List.of(List.of(event("ORDER_CREATED",7L,null)));scopes=List.of(List.of(scope(8L)));
            assertSame(error,assertThrows(RuntimeException.class,()->service.reconcile(DATE)));
            assertEquals(stage,calls.get(calls.size()-1));
        }
    }
}
