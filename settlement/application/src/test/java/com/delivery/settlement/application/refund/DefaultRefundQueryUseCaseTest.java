package com.delivery.settlement.application.refund;

import com.delivery.settlement.application.api.refund.*;
import com.delivery.settlement.domain.refund.RefundPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultRefundQueryUseCaseTest {
    private AdminRefundCase row(Long principal) {
        return new AdminRefundCase(UUID.randomUUID(),UUID.randomUUID(),"key",1L,2L,principal,3L,
                "PENDING","CANCELLED","ONLINE","ORDER_CANCELLED","ORDER_TOTAL","MANUAL_REVIEW","VND",
                null,null,null,null,null,null,"SYSTEM",null,"private reason","private provider","private error",0,null,null,null);
    }
    static class Store implements RefundQueryStore {
        List<AdminRefundCase> rows=List.of();String query;int limit,fallbacks;Optional<AdminRefundCase> byId=Optional.empty();
        @Override public List<AdminRefundCase> allAdmin(int size){query="all";limit=size;return rows;}
        @Override public List<AdminRefundCase> adminByStatus(RefundPolicy.Status status,int size){query=status.name();limit=size;return rows;}
        @Override public Optional<AdminRefundCase> byId(UUID id){return byId;}
        @Override public List<AdminRefundCase> legacyUser(Long id,int size){query="legacy";limit=size;return rows;}
        @Override public List<AdminRefundCase> principal(Long id,int size){query="principal";limit=size;return rows;}
        @Override public List<AdminRefundCase> principalOrUnmigratedLegacy(Long principal,Long user,int size){query="compatibility";limit=size;return rows;}
        @Override public void legacyFallback(){fallbacks++;}
    }
    @Test void adminReadsRetainStatusFilteringLimitsAndMissingCasePolicy() {
        var store=new Store();store.rows=List.of(row(20L));var core=new DefaultRefundQueryUseCase(store,false);
        assertThat(core.adminCases(null,1000)).isSameAs(store.rows);assertThat(store.query).isEqualTo("all");assertThat(store.limit).isEqualTo(100);
        core.adminCases(RefundPolicy.Status.MANUAL_REVIEW,0);assertThat(store.query).isEqualTo("MANUAL_REVIEW");assertThat(store.limit).isEqualTo(1);
        assertThatThrownBy(()->core.adminCase(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("refundId is required");
        var id=UUID.randomUUID();assertThatThrownBy(()->core.adminCase(id)).isInstanceOf(RefundCaseMissing.class);
        store.byId=Optional.of(store.rows.get(0));assertThat(core.adminCase(id)).isSameAs(store.rows.get(0));
    }
    @Test void customerScopeUsesPrincipalPolicyAndCountsOnlyActualLegacyRows() {
        var store=new Store();store.rows=List.of(row(20L),row(null));var core=new DefaultRefundQueryUseCase(store,false);
        var result=core.customerCases(20L,2L,50);assertThat(store.query).isEqualTo("compatibility");assertThat(store.fallbacks).isEqualTo(1);
        assertThat(result).hasSize(2);assertThat(result.get(0).refundId()).isEqualTo(store.rows.get(0).refundId());
        assertThat(CustomerRefundCase.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("eventId","idempotencyKey","reason","actorSource","actorId","providerReference","lastError","attempts");
        new DefaultRefundQueryUseCase(store,true).customerCases(20L,2L,1000);assertThat(store.query).isEqualTo("principal");assertThat(store.limit).isEqualTo(100);assertThat(store.fallbacks).isEqualTo(1);
        core.customerCases(2L,-10);assertThat(store.query).isEqualTo("legacy");assertThat(store.limit).isEqualTo(1);assertThat(store.fallbacks).isEqualTo(1);
    }
    @Test void invalidCustomerIdentitiesFailBeforeAnyStoreQuery() {
        var store=new Store();var core=new DefaultRefundQueryUseCase(store,false);
        for(Long invalid:new Long[]{null,0L,-1L}) {
            assertThatThrownBy(()->core.customerCases(invalid,50)).isInstanceOf(IllegalArgumentException.class).hasMessage("userId is required");
            assertThatThrownBy(()->core.customerCases(invalid,2L,50)).isInstanceOf(IllegalArgumentException.class).hasMessage("principalId and legacyUserId are required");
            assertThatThrownBy(()->core.customerCases(20L,invalid,50)).isInstanceOf(IllegalArgumentException.class).hasMessage("principalId and legacyUserId are required");
        }
        assertThat(store.query).isNull();
    }
}
