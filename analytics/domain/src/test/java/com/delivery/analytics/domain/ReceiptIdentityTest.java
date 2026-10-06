package com.delivery.analytics.domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ReceiptIdentityTest {
    private Object[] values() {return new Object[]{"ORDER_CREATED",1L,2L,3L,"Shop",new BigDecimal("10.00"),"PENDING","COD",4L,"raw","hash"};}
    private ReceiptIdentity identity(Object[] v) {return new ReceiptIdentity((String)v[0],(Long)v[1],(Long)v[2],(Long)v[3],(String)v[4],
            (BigDecimal)v[5],(String)v[6],(String)v[7],(Long)v[8],(String)v[9],(String)v[10]);}
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,10})
    void contradictionTruthTable(int field) {
        Object[] changed=values();
        changed[field]=changed[field] instanceof String?"other":changed[field] instanceof BigDecimal?BigDecimal.ONE:99L;
        var error=assertThrows(IllegalArgumentException.class,()->identity(values()).requireExactReplay(identity(changed)));
        assertEquals("analytics deduplication key replay has contradictory identity or payload",error.getMessage());
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8})
    void nullIsDistinctFromPresent(int field) {
        Object[] changed=values();changed[field]=null;
        assertThrows(IllegalArgumentException.class,()->identity(values()).requireExactReplay(identity(changed)));
        assertThrows(IllegalArgumentException.class,()->identity(changed).requireExactReplay(identity(values())));
    }
    @Test void scaleInsensitiveAndFingerprintOverridesRawText() {
        Object[] changed=values();changed[5]=BigDecimal.TEN;changed[9]="different raw";
        identity(values()).requireExactReplay(identity(changed));
    }
    @Test void legacyFallbackRequiresExactRawTextIncludingNull() {
        Object[] legacy=values();legacy[10]=null;
        identity(legacy).requireExactReplay(identity(values()));
        Object[] different=values();different[9]="different";
        assertThrows(IllegalArgumentException.class,()->identity(legacy).requireExactReplay(identity(different)));
        legacy[9]=null;different[9]=null;
        identity(legacy).requireExactReplay(identity(different));
        assertThrows(IllegalArgumentException.class,()->identity(legacy).requireExactReplay(identity(values())));
    }
    @Test void bothAbsentAmountsAreEqualButPresentAmountConflicts() {
        Object[] absent=values();absent[5]=null;
        identity(absent).requireExactReplay(identity(absent));
        assertThrows(IllegalArgumentException.class,()->identity(absent).requireExactReplay(identity(values())));
    }
    @Test void corruptNullTypeStillThrowsNullPointerException() {
        Object[] corrupt=values();corrupt[0]=null;
        assertThrows(NullPointerException.class,()->identity(corrupt).requireExactReplay(identity(values())));
    }
}
