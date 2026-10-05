package com.delivery.notification.application;

import com.delivery.notification.application.api.InboxPort;
import com.delivery.notification.domain.InboxActor;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InboxTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026,10,5,12,0);
    static final InboxActor LEGACY = new InboxActor(null,2L,true,false);
    static final InboxActor PRINCIPAL = new InboxActor(1L,2L,false,true);
    static final InboxActor FALLBACK = new InboxActor(1L,2L,false,false);
    static class Row { Boolean read; LocalDateTime at; Row(Boolean read) {this.read=read;} }
    static class Fixture implements InboxPort<Row,Row> {
        List<String> calls = new ArrayList<>(); List<Row> rows = new ArrayList<>(); Row row = new Row(false);
        RuntimeException ownedFailure;
        public List<Row> list(InboxActor actor, boolean unread, int limit) { calls.add("list:"+unread+":"+limit); return rows; }
        public Row owned(Long id, InboxActor actor) {calls.add("owned:"+id); if(ownedFailure!=null) throw ownedFailure; return row;}
        public Row response(Row row) {calls.add("response"); return row;}
        public Boolean isRead(Row row) {return row.read;}
        public void setRead(Row row, LocalDateTime at) {calls.add("setRead"); row.read=true; row.at=at;}
        public void save(Row row) {calls.add("save");}
        public void saveAll(List<Row> rows) {calls.add("saveAll");}
        public void fallback(List<Row> rows, String surface) {calls.add(surface);}
        public long unreadCount(InboxActor actor) {calls.add("count"); return 101;}
        public void delete(Row row) {calls.add("delete");}
        public void deleteLegacy(Long id, Long user) {assertEquals(2L,user); calls.add("deleteLegacy");}
        public void markLegacy(Long id, Long user, LocalDateTime at) {assertEquals(2L,user); assertEquals(NOW,at); calls.add("markLegacy");}
        public int markAllLegacy(Long user, LocalDateTime at) {assertEquals(2L,user); assertEquals(NOW,at); calls.add("markAllLegacy"); return 101;}
        Inbox<Row,Row> inbox() {return new Inbox<>(this, () -> {calls.add("clock"); return NOW;});}
    }
    @Test void validationOrderAndNoPersistenceOnInvalidIdentity() {
        var f = new Fixture(); var inbox=f.inbox();
        for(InboxActor actor : List.of(new InboxActor(null,0L,true,false),new InboxActor(null,0L,false,false),new InboxActor(1L,0L,false,true))) {
            assertThrows(IllegalArgumentException.class, () -> inbox.list(actor,false));
            assertThrows(IllegalArgumentException.class, () -> inbox.unreadCount(actor));
            assertThrows(IllegalArgumentException.class, () -> inbox.markAllRead(actor));
            for(Long id : new Long[]{null,0L,-1L,1L}) {
                var ex=assertThrows(IllegalArgumentException.class, () -> inbox.get(id,actor));
                if(id==null || id<=0) assertEquals("notificationId must be positive",ex.getMessage());
                assertThrows(IllegalArgumentException.class, () -> inbox.markRead(id,actor));
                assertThrows(IllegalArgumentException.class, () -> inbox.delete(id,actor));
            }
        }
        assertTrue(f.calls.isEmpty());
    }
    @Test void listGetAndCountPreserveFallbackSurfacesAndCap() {
        for(InboxActor actor : List.of(LEGACY,PRINCIPAL,FALLBACK)) for(boolean unread : new boolean[]{false,true}) {
            var f=new Fixture(); f.rows.add(f.row); var inbox=f.inbox();
            assertEquals(List.of(f.row),inbox.list(actor,unread));
            var expected=new ArrayList<>(List.of("list:"+unread+":100"));
            if(actor.recordFallback()) expected.add(unread?"inbox_unread_list":"inbox_list"); expected.add("response");
            assertEquals(expected,f.calls); f.calls.clear();
            assertSame(f.row,inbox.get(9L,actor));
            assertEquals(actor.recordFallback()?List.of("owned:9","inbox_read","response"):List.of("owned:9","response"),f.calls);
            f.calls.clear(); assertEquals(101,inbox.unreadCount(actor)); assertEquals(List.of("count"),f.calls);
        }
    }
    @Test void principalMarkReadIsIdempotentAndNullReadMeansUnread() {
        for(InboxActor actor : List.of(PRINCIPAL,FALLBACK)) for(Boolean read : new Boolean[]{null,false,true}) {
            var f=new Fixture(); f.row.read=read; var old=NOW.minusDays(1); f.row.at=old;
            assertSame(f.row,f.inbox().markRead(9L,actor));
            var expected=new ArrayList<>(List.of("owned:9")); if(actor.recordFallback()) expected.add("inbox_mark_read");
            if(!Boolean.TRUE.equals(read)) expected.addAll(List.of("clock","setRead","save")); expected.add("response");
            assertEquals(expected,f.calls); assertEquals(Boolean.TRUE.equals(read)?old:NOW,f.row.at);
        }
    }
    @Test void nullLegacyListRetainsMapperEmptyListBehavior() {
        var f = new Fixture(); f.rows = null;
        assertTrue(f.inbox().list(LEGACY, false).isEmpty());
    }
    @Test void legacyMarkAndBulkRetainDatabaseOperations() {
        var f=new Fixture(); assertSame(f.row,f.inbox().markRead(9L,LEGACY));
        assertEquals(List.of("clock","markLegacy","owned:9","response"),f.calls); f.calls.clear();
        assertEquals(101,f.inbox().markAllRead(LEGACY)); assertEquals(List.of("clock","markAllLegacy"),f.calls);
    }
    @Test void principalBulkKeepsBoundedListSharedTimestampAndEmptySave() {
        for(InboxActor actor : List.of(PRINCIPAL,FALLBACK)) for(int size : new int[]{0,2}) {
            var f=new Fixture(); for(int i=0;i<size;i++) f.rows.add(new Row(false));
            assertEquals(size,f.inbox().markAllRead(actor));
            var expected=new ArrayList<>(List.of("list:true:100")); if(actor.recordFallback()) expected.add("inbox_mark_all_read"); expected.add("clock");
            for(int i=0;i<size;i++) expected.add("setRead"); expected.add("saveAll"); assertEquals(expected,f.calls);
            f.rows.forEach(row -> {assertTrue(row.read); assertEquals(NOW,row.at);});
        }
    }
    @Test void deleteAndMissingOwnershipStopAtCorrectBoundary() {
        for(InboxActor actor : List.of(LEGACY,PRINCIPAL,FALLBACK)) {
            var f=new Fixture(); f.inbox().delete(9L,actor);
            assertEquals(actor.legacyOnly()?List.of("deleteLegacy"):actor.recordFallback()?List.of("owned:9","inbox_delete","delete"):List.of("owned:9","delete"),f.calls);
            f.calls.clear(); f.ownedFailure=new IllegalStateException("not owned");
            assertSame(f.ownedFailure,assertThrows(IllegalStateException.class, () -> f.inbox().get(9L,actor)));
            assertEquals(List.of("owned:9"),f.calls);
        }
    }
}
