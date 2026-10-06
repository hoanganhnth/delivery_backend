package com.delivery.analytics.application;
import com.delivery.analytics.applicationapi.*;
import com.delivery.analytics.applicationapi.IngestionPorts.*;
import com.delivery.analytics.domain.*;
import com.delivery.analytics.domain.SnapshotDecisions.Item;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class IngestionServiceTest {
    private static final LocalDate TODAY=LocalDate.of(2026,10,6), EVENT_DATE=TODAY.minusDays(2);
    private static final Item ITEM=new Item(9,2,BigDecimal.TEN,"Shop");
    private final List<String> calls=new ArrayList<>();
    private boolean accepted=true, enabled=true;
    private String failure;
    private final RuntimeException error=new IllegalStateException("storage unavailable");
    private void call(String action) {calls.add(action);if(action.equals(failure)) throw error;}
    private final IngestionService service=new IngestionService((key,identity)-> {
        call("claim:"+key); assertEquals("raw",identity.rawPayload());return accepted;
    },new Payloads() {
        public LocalDate eventDate(String payload,LocalDate fallback) {call("date");assertEquals(TODAY,fallback);return EVENT_DATE;}
        public List<Item> items(String payload) {call("parse-all-items");return List.of(ITEM,ITEM);}
    },new Projections() {
        public void order(String type,LocalDate date,Long id,BigDecimal amount) {
            assertEquals(TODAY,date);call(type+":"+id);
        }
        public void payment(String type,LocalDate date,BigDecimal amount) {assertEquals(TODAY,date);call(type);}
        public boolean itemsEnabled(Long id) {call("items-enabled");return enabled && id!=null;}
        public void item(LocalDate date,Long id,Item item,boolean cancelled) {
            assertEquals(EVENT_DATE,date);assertEquals(7L,id);assertEquals(ITEM,item);call("item:"+cancelled);
        }
    },()->{call("today");return TODAY;});
    private boolean ingest(String type,Long restaurant) {
        return service.ingest(new IngestionUseCase.Command("key",new ReceiptIdentity(type,1L,2L,restaurant,"Shop",
                BigDecimal.TEN,"status","COD",1L,"raw","hash")));
    }
    @ParameterizedTest @ValueSource(strings={"ORDER_CREATED","ORDER_CANCELLED"})
    void claimsThenProjectsPlatformRestaurantAndCompleteItems(String type) {
        assertTrue(ingest(type,7L));
        assertEquals(List.of("claim:key","today","date",type+":null",type+":7","items-enabled","parse-all-items",
                "item:"+type.equals("ORDER_CANCELLED"),"item:"+type.equals("ORDER_CANCELLED")),calls);
    }
    @ParameterizedTest @ValueSource(strings={"ORDER_CREATED","ORDER_CANCELLED","ORDER_DELIVERED","PAYMENT_COMPLETED","PAYMENT_FAILED"})
    void exactReplayHasNoClockPayloadOrProjectionSideEffects(String type) {
        accepted=false;assertFalse(ingest(type,7L));assertEquals(List.of("claim:key"),calls);
    }
    @Test void deliveredDoesNotParseItemsOrTimestamp() {
        ingest("ORDER_DELIVERED",7L);
        assertEquals(List.of("claim:key","today","ORDER_DELIVERED:null","ORDER_DELIVERED:7"),calls);
    }
    @ParameterizedTest @ValueSource(strings={"PAYMENT_COMPLETED","PAYMENT_FAILED"})
    void paymentIsPlatformOnly(String type) {ingest(type,7L);assertEquals(List.of("claim:key","today",type),calls);}
    @Test void absentRestaurantPreservesDateValidationButSkipsItems() {
        ingest("ORDER_CREATED",null);
        assertEquals(List.of("claim:key","today","date","ORDER_CREATED:null","items-enabled"),calls);
    }
    @Test void legacyFixtureWithItemsDisabledDoesNotValidateSnapshot() {
        enabled=false;ingest("ORDER_CREATED",7L);assertFalse(calls.contains("parse-all-items"));
    }
    @ParameterizedTest @ValueSource(strings={"claim:key","today","date","ORDER_CREATED:null","ORDER_CREATED:7","parse-all-items","item:false"})
    void failurePropagatesWithoutFurtherOperations(String stage) {
        failure=stage;assertSame(error,assertThrows(RuntimeException.class,()->ingest("ORDER_CREATED",7L)));
        assertEquals(stage,calls.get(calls.size()-1));
        if(stage.equals("parse-all-items")) assertFalse(calls.contains("item:false"));
    }
    @Test void invalidInternalCommandIsRejected() {
        var exception=assertThrows(IllegalArgumentException.class,()->ingest("unknown",null));
        assertEquals("Unsupported analytics event type: unknown",exception.getMessage());
    }
}
