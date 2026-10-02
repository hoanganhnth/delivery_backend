package com.delivery.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.restaurant.application.api.RestaurantManagementQuery;
import com.delivery.restaurant.application.api.RestaurantManagementReadUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementResult;
import com.delivery.restaurant.application.api.RestaurantPageSlice;
import com.delivery.restaurant.application.api.RestaurantReadPort;
import com.delivery.restaurant.application.api.RestaurantReadUseCase;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultRestaurantReadUseCaseTest {

    private final RecordingReadPort port = new RecordingReadPort();
    private final RestaurantReadUseCase publicReads = new DefaultRestaurantReadUseCase(port);
    private final RestaurantManagementReadUseCase managementReads =
            new DefaultRestaurantManagementReadUseCase(port);

    @Test
    void detailResolvesActivePausedArchivedAndMissingRows() {
        RestaurantSnapshot active = snapshot(1L, RestaurantStatus.ACTIVE);
        RestaurantSnapshot paused = snapshot(2L, RestaurantStatus.PAUSED);
        RestaurantSnapshot archived = snapshot(3L, RestaurantStatus.ARCHIVED);
        port.detail = Optional.of(archived);

        assertThat(publicReads.findById(3L)).contains(archived);
        port.detail = Optional.of(active);
        assertThat(publicReads.findById(1L)).contains(active);
        port.detail = Optional.of(paused);
        assertThat(publicReads.findById(2L)).contains(paused);
        port.detail = Optional.empty();
        assertThat(publicReads.findById(404L)).isEmpty();
    }

    @Test
    void publicListAndSearchUseArchivedExcludingBoundedQueriesWithoutRewritingKeyword() {
        RestaurantSnapshot active = snapshot(1L, RestaurantStatus.ACTIVE);
        RestaurantSnapshot paused = snapshot(2L, RestaurantStatus.PAUSED);
        port.publicList = List.of(active, paused);
        port.search = List.of(active);

        assertThat(publicReads.listPublic()).containsExactly(active, paused);
        assertThat(port.lastPublicLimit).isEqualTo(100);
        assertThat(publicReads.searchPublic("  Pizza  ")).containsExactly(active);
        assertThat(port.lastSearchKeyword).isEqualTo("  Pizza  ");
        assertThat(port.lastSearchLimit).isEqualTo(100);
    }

    @Test
    void pagedPublicReadTrimsOnlyNonblankKeywordAndPreservesPageMetadata() {
        RestaurantPageSlice page = new RestaurantPageSlice(
                List.of(snapshot(1L, RestaurantStatus.ACTIVE)), 2, 20, 41, 3, true);
        port.page = page;

        assertThat(publicReads.pagePublic(2, 20, "  pizza  ")).isSameAs(page);
        assertThat(port.lastPageKeyword).isEqualTo("pizza");
        assertThat(publicReads.pagePublic(0, 24, "  ")).isSameAs(page);
        assertThat(port.lastPageKeyword).isEqualTo("  ");
        assertThat(publicReads.pagePublic(0, 24, null)).isSameAs(page);
        assertThat(port.lastPageKeyword).isNull();
    }

    @Test
    void adminAndOwnerManagementReadsPreserveArchivedRowsAndLegacyResult() {
        RestaurantManagementResult adminRows = new RestaurantManagementResult(
                List.of(snapshot(1L, RestaurantStatus.ARCHIVED)), 0);
        port.management = adminRows;
        assertThat(managementReads.readForAdmin(1L, 1L, false)).isSameAs(adminRows);
        assertThat(port.lastManagementQuery.actorRole()).isEqualTo(RestaurantActorRole.ADMIN);
        assertThat(port.lastManagementQuery.limit()).isEqualTo(100);

        RestaurantManagementResult ownerRows = new RestaurantManagementResult(
                List.of(snapshot(2L, RestaurantStatus.ARCHIVED)), 1);
        port.management = ownerRows;
        assertThat(managementReads.readForOwner(101L, 7L, false)).isSameAs(ownerRows);
        assertThat(port.lastManagementQuery.actorRole()).isEqualTo(RestaurantActorRole.SHOP_OWNER);
        assertThat(port.lastManagementQuery.principalId()).isEqualTo(101L);
        assertThat(port.lastManagementQuery.legacyUserId()).isEqualTo(7L);
        assertThat(port.lastManagementQuery.principalOwnershipEnforced()).isFalse();

        assertThat(managementReads.readForOwner(101L, 7L, true)).isSameAs(ownerRows);
        assertThat(port.lastManagementQuery.principalOwnershipEnforced()).isTrue();
    }

    @Test
    void managementReadsRejectMissingOrUnsupportedActorsBeforeRepositoryRead() {
        assertThatThrownBy(() -> managementReads.readForOwner(null, 7L, false))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> managementReads.readForOwner(101L, null, false))
                .isInstanceOf(ManagementAccessException.class);
        assertThatThrownBy(() -> managementReads.readForOwner(0L, 7L, false))
                .isInstanceOf(ManagementAccessException.class);
        assertThat(port.managementCalls).isZero();
    }

    private RestaurantSnapshot snapshot(Long id, RestaurantStatus status) {
        return new RestaurantSnapshot(id, "Restaurant " + id, "123 Main Street", "0123456789",
                LocalTime.of(9, 0), LocalTime.of(18, 0), 30, "image.png", "Description",
                10.8, 106.7, 4.5, 2, status, 1L, "Asia/Ho_Chi_Minh", 101L, 7L);
    }

    private static final class RecordingReadPort implements RestaurantReadPort {
        private Optional<RestaurantSnapshot> detail = Optional.empty();
        private List<RestaurantSnapshot> publicList = List.of();
        private List<RestaurantSnapshot> search = List.of();
        private RestaurantPageSlice page = new RestaurantPageSlice(List.of(), 0, 24, 0, 0, false);
        private RestaurantManagementResult management = new RestaurantManagementResult(List.of(), 0);
        private String lastSearchKeyword;
        private String lastPageKeyword;
        private int lastPublicLimit;
        private int lastSearchLimit;
        private RestaurantManagementQuery lastManagementQuery;
        private int managementCalls;

        @Override
        public Optional<RestaurantSnapshot> findById(Long id) {
            return detail;
        }

        @Override
        public List<RestaurantSnapshot> findPublic(int limit) {
            lastPublicLimit = limit;
            return publicList;
        }

        @Override
        public List<RestaurantSnapshot> searchPublic(String keyword, int limit) {
            lastSearchKeyword = keyword;
            lastSearchLimit = limit;
            return search;
        }

        @Override
        public RestaurantPageSlice pagePublic(int page, int size, String keyword) {
            lastPageKeyword = keyword;
            return this.page;
        }

        @Override
        public RestaurantManagementResult findManagement(RestaurantManagementQuery query) {
            managementCalls++;
            lastManagementQuery = query;
            return management;
        }
    }
}
