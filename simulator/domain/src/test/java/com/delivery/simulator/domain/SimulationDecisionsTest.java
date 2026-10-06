package com.delivery.simulator.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SimulationDecisionsTest {
    @Test void projectionTruthTable() {
        String[] states = {null,"","NONE","PENDING","ASSIGNED","PICKED_UP","DELIVERING","DELIVERED","CANCELLED","SHIPPER_NOT_FOUND","REJECTED"};
        for (String order: states) for (String delivery: states) {
            boolean terminal = "DELIVERED".equals(order)||"CANCELLED".equals(order)||"SHIPPER_NOT_FOUND".equals(order);
            boolean expected = terminal && ("DELIVERED".equals(order) ? "DELIVERED".equals(delivery)
                    : "NONE".equals(delivery)||"CANCELLED".equals(delivery)||"SHIPPER_NOT_FOUND".equals(delivery));
            assertThat(SimulationDecisions.terminalOrder(order)).isEqualTo(terminal);
            assertThat(SimulationDecisions.converged(order,delivery)).isEqualTo(expected);
        }
        for (String s: List.of("PASSED","PARTIAL","FAILED","ABORTED","PAUSED","RUNNING",""))
            assertThat(SimulationDecisions.terminalRun(s)).isEqualTo(Set.of("PASSED","PARTIAL","FAILED","ABORTED").contains(s));
        assertThatThrownBy(()->SimulationDecisions.terminalRun(null)).isInstanceOf(NullPointerException.class);
    }
    @Test void releaseAndRetryBoundaries() {
        assertThat(SimulationDecisions.actorReleaseSafe(null,null)).isTrue();
        assertThatThrownBy(()->SimulationDecisions.actorReleaseSafe(1L,null)).isInstanceOf(NullPointerException.class);
        for(String s:List.of("NONE","DELIVERED","CANCELLED","SHIPPER_NOT_FOUND","PICKED_UP","REJECTED"))
            assertThat(SimulationDecisions.actorReleaseSafe(1L,s)).isEqualTo(Set.of("NONE","DELIVERED","CANCELLED","SHIPPER_NOT_FOUND").contains(s));
        for(Integer status:Arrays.asList(null,200,401,404,429,502)) for(int attempt:new int[]{Integer.MIN_VALUE,-1,0,1,4,5,7,8,9,Integer.MAX_VALUE}) {
            assertThat(SimulationDecisions.retryLocation(status,attempt)).isEqualTo(Objects.equals(status,429)&&attempt<8);
            long expected = Math.min(30_000L,1000L << Math.max(0,Math.min(8,attempt)));
            assertThat(SimulationDecisions.locationRetryDelayMillis(attempt)).isEqualTo(expected);
        }
    }
    @Test void coordinatesAndControlTruthTable() {
        for(double lat:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,7.99,8,16,24,24.01,Double.POSITIVE_INFINITY})
            for(double lng:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,101.99,102,106,110,110.01,Double.POSITIVE_INFINITY})
                assertThat(SimulationDecisions.coordinateValid(lat,lng)).isEqualTo(Double.isFinite(lat)&&Double.isFinite(lng)&&lat>=8&&lat<=24&&lng>=102&&lng<=110);
        for(String s:Arrays.asList(null,"PENDING","CONFIRMED")) for(boolean enabled:new boolean[]{false,true})
            for(String token:List.of("","  ","token")) for(boolean trigger:new boolean[]{false,true})
                assertThat(SimulationDecisions.autoConfirm(s,enabled,token,trigger)).isEqualTo("PENDING".equals(s)&&enabled&&!token.isBlank()&&!trigger);
    }
    @Test void aliasesKeepFirstMatchAndBlankNullSemantics() {
        var aliases = List.of(new SimulationDecisions.ActorAlias("one","1"),new SimulationDecisions.ActorAlias("two","1"));
        assertThat(SimulationDecisions.canonicalShipper(aliases,"1")).isEqualTo("one");
        assertThat(SimulationDecisions.canonicalShipper(aliases,"two")).isEqualTo("two");
        for(String raw:List.of(""," ","null")) assertThat(SimulationDecisions.canonicalShipper(aliases,raw)).isEmpty();
        assertThat(SimulationDecisions.canonicalShipper(aliases,"unknown")).isEqualTo("unknown");
        assertThatThrownBy(()->SimulationDecisions.canonicalShipper(aliases,null)).isInstanceOf(NullPointerException.class);
    }
}
