package com.delivery.analytics_service.controller;

import com.delivery.analytics_service.dto.DashboardResponse;
import com.delivery.analytics_service.scheduler.StatsReconciliationJob;
import com.delivery.analytics_service.service.DashboardQueryService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DashboardControllerAuthorizationTest {

    private final DashboardQueryService queryService = mock(DashboardQueryService.class);
    private final StatsReconciliationJob reconciliationJob = mock(StatsReconciliationJob.class);
    private final DashboardController controller = new DashboardController(queryService, reconciliationJob);

    @Test
    void adminDashboardRejectsNonAdminBeforeQueryingProjection() {
        AuthenticatedActor owner = new AuthenticatedActor(10L, 10L, "owner@test.dev", Set.of("SHOP_OWNER"));

        var response = controller.getAdminDashboard("month", null, owner);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getStatus()).isZero();
        verifyNoInteractions(queryService);
    }

    @Test
    void adminDashboardRejectsUnknownPeriod() {
        AuthenticatedActor admin = new AuthenticatedActor(11L, 11L, "admin@test.dev", Set.of("ADMIN"));

        var response = controller.getAdminDashboard("daily", null, admin);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getStatus()).isZero();
        verifyNoInteractions(queryService);
    }

    @Test
    void adminDashboardAllowsAdminProjectionRead() {
        AuthenticatedActor admin = new AuthenticatedActor(11L, 11L, "admin@test.dev", Set.of("ADMIN"));

        var response = controller.getAdminDashboard("quarter", 2026, admin);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void reconcileRejectsNonAdmin() {
        AuthenticatedActor owner = new AuthenticatedActor(10L, 10L, "owner@test.dev", Set.of("SHOP_OWNER"));

        var response = controller.manualReconcile("2026-08-22", owner);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(reconciliationJob);
    }
}
