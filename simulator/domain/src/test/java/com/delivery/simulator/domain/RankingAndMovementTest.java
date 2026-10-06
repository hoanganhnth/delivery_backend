package com.delivery.simulator.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class RankingAndMovementTest {
    @Test void movementPreservesSeedDefectAndNumericEdges() {
        for(long seed:new long[]{Long.MIN_VALUE,-1,0,1,42,Long.MAX_VALUE}) {
            var route=new DeterministicPolyline(10,106,11,107,seed);
            assertThat(route.positionAfterSeconds(10,30)).isEqualTo(new DeterministicPolyline(10,106,11,107,0).positionAfterSeconds(10,30));
            assertThat(route.positionAfterSeconds(0,30).latitude()).isEqualTo(10);
            assertThat(route.positionAfterSeconds(-1,30).latitude()).isEqualTo(10);
            assertThat(route.positionAfterSeconds(10,0).longitude()).isEqualTo(106);
            assertThat(route.positionAfterSeconds(10,-1).longitude()).isEqualTo(106);
            assertThat(route.positionAfterSeconds(Long.MAX_VALUE,30).latitude()).isEqualTo(11);
            assertThat(route.positionAfterSeconds(10,Double.NaN).latitude()).isNaN();
        }
        assertThat(new DeterministicPolyline(10,106,10,106,1).positionAfterSeconds(100,30).latitude()).isEqualTo(10);
        assertThat(new DeterministicPolyline(0,0,0,-1,1).positionAfterSeconds(1,30).headingDegrees()).isEqualTo(270);
        assertThat(new DeterministicPolyline(Double.NaN,0,1,1,1).positionAfterSeconds(1,30).latitude()).isNaN();
    }
    @Test void oracleEligibilityOrderingAndNonfiniteValues() {
        var inputs=List.of(new CandidateOracle.Input("first","first",10,106,100,true),
                new CandidateOracle.Input("tie","tie",10,106,99,true),new CandidateOracle.Input("offline","offline",10,106,0,false),
                new CandidateOracle.Input("far","far",11,107,100,true),new CandidateOracle.Input("nan","nan",Double.NaN,106,100,true));
        var values=CandidateOracle.evaluate(inputs,10,106,100,0);
        assertThat(values).extracting(CandidateOracle.Candidate::id).containsExactly("first","tie","offline","far","nan");
        assertThat(values.get(0).eligible()).isTrue(); assertThat(values.get(1).reason()).contains("COD");
        assertThat(values.get(2).reason()).contains("offline"); assertThat(values.get(4).distance()).isInfinite();
        assertThat(values.get(4).score()).isZero();
        for(int axis=0;axis<4;axis++) {
            double[] coordinates={10,106,10,106}; coordinates[axis]=Double.POSITIVE_INFINITY;
            assertThat(CandidateOracle.evaluate(List.of(new CandidateOracle.Input("x","x",coordinates[0],coordinates[1],0,true)),coordinates[2],coordinates[3],0,1).get(0).distance()).isInfinite();
        }
        assertThat(CandidateOracle.evaluate(List.of(),0,0,0,1)).isEmpty();
        assertThatThrownBy(()->values.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    private ShadowRanking.Candidate candidate(long id,double distance,boolean off,boolean cod,boolean reasons,String state) {
        return new ShadowRanking.Candidate(id,distance,off,cod,reasons,"WHY",state,"MATCH_REJECTED");
    }
    @Test void shadowTruthTableAndReasonPrecedence() {
        for(long id:new long[]{-1,1}) for(double distance:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0,1})
            for(boolean off:new boolean[]{false,true}) for(boolean cod:new boolean[]{false,true})
                for(boolean reasons:new boolean[]{false,true}) for(String state:List.of("SELECTED","rejected")) {
                    var result=ShadowRanking.rank(List.of(candidate(id,distance,off,cod,reasons,state)),List.of(),false);
                    var score=result.scores().get(0);
                    boolean eligible=id>0&&Double.isFinite(distance)&&distance>=0&&!off&&!cod&&!reasons&&!"rejected".equals(state);
                    assertThat(score.eligible()).isEqualTo(eligible);
                    assertThat(result.recommended()).isEqualTo(eligible?id:-1);
                    if(!eligible) assertThat(score.exclusionReason()).isEqualTo(id<=0?"MISSING_SHIPPER_ID":!Double.isFinite(distance)||distance<0?"MISSING_DISTANCE":reasons?"WHY":off?"OFFLINE":cod?"COD_INELIGIBLE":"MATCH_REJECTED");
                }
    }
    @Test void shadowDefaultsDuplicateActorsTieFairnessAndOverflow() {
        var candidates=List.of(candidate(1,1,false,false,false,""),candidate(2,1,false,false,false,""));
        for(boolean balanced:new boolean[]{false,true}) assertThat(ShadowRanking.rank(candidates,List.of(),balanced).recommended()).isEqualTo(1);
        var actors=List.of(new ShadowRanking.Actor(-1,20,0),new ShadowRanking.Actor(1,60,100),new ShadowRanking.Actor(1,Double.NaN,100),new ShadowRanking.Actor(2,-1,-1),new ShadowRanking.Actor(0,0,0),new ShadowRanking.Actor(2,Double.POSITIVE_INFINITY,0));
        assertThat(ShadowRanking.rank(candidates,actors,false).recommended()).isEqualTo(1);
        assertThat(ShadowRanking.rank(candidates,actors,true).recommended()).isEqualTo(2);
        assertThat(ShadowRanking.rank(candidates,actors,false).scores().get(0).speed()).isEqualTo(60);
        assertThat(ShadowRanking.rank(candidates,actors,false).scores().get(1).completed()).isZero();
        var overflow=ShadowRanking.rank(List.of(candidate(1,Double.MAX_VALUE,false,false,false,"")),List.of(new ShadowRanking.Actor(1,Double.MIN_VALUE,0)),true);
        assertThat(overflow.recommended()).isEqualTo(-1); assertThat(overflow.scores().get(0).eligible()).isTrue();
        assertThat(overflow.scores().get(0).eta()).isInfinite();
        assertThatThrownBy(()->overflow.scores().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
