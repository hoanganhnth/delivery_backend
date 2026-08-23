package com.delivery.analytics_service.controller;

import com.delivery.analytics_service.dto.DashboardResponse;
import com.delivery.analytics_service.payload.BaseResponse;
import com.delivery.analytics_service.scheduler.StatsReconciliationJob;
import com.delivery.analytics_service.service.DashboardQueryService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.analytics.processing-enabled", havingValue = "true")
public class DashboardController {

    private final DashboardQueryService queryService;
    private final StatsReconciliationJob reconciliationJob;

    @GetMapping("/dashboard/admin")
    public ResponseEntity<BaseResponse<DashboardResponse.AdminDashboard>> getAdminDashboard(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (actor == null || !actor.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can access analytics dashboard"));
        }
        String normalizedPeriod = period == null ? "month" : period.trim().toLowerCase(Locale.ROOT);
        if (!normalizedPeriod.equals("month") && !normalizedPeriod.equals("quarter")
                && !normalizedPeriod.equals("year")) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Unsupported analytics period"));
        }
        if (year != null && (year < 2000 || year > 2100)) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Analytics year is out of range"));
        }

        DashboardResponse.AdminDashboard response = queryService.getAdminDashboard(normalizedPeriod, year);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê Admin thành công"));
    }

    @GetMapping("/dashboard/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<DashboardResponse.RestaurantDashboard>> getRestaurantDashboard(
            @PathVariable Long restaurantId,
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (actor == null || (!actor.isAdmin() && !actor.isShopOwner())) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN or SHOP_OWNER can access analytics"));
        }

        DashboardResponse.RestaurantDashboard response = queryService.getRestaurantDashboard(
                restaurantId, period, year);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê nhà hàng thành công"));
    }

    @GetMapping("/dashboard/my-restaurant")
    public ResponseEntity<BaseResponse<DashboardResponse.RestaurantDashboard>> getMyRestaurantDashboard(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long restaurantId,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (actor == null || (!actor.isAdmin() && !actor.isShopOwner())) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN or SHOP_OWNER can access analytics"));
        }

        Long targetId = restaurantId;
        if (targetId == null) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "restaurantId is required"));
        }
        DashboardResponse.RestaurantDashboard response = queryService.getRestaurantDashboard(
                targetId, period, year);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê nhà hàng thành công"));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<BaseResponse<String>> manualReconcile(
            @RequestParam String date,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || !actor.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can reconcile analytics"));
        }
        LocalDate targetDate = LocalDate.parse(date);
        reconciliationJob.reconcileDate(targetDate);
        return ResponseEntity.ok(new BaseResponse<>(1, "Reconciliation completed for " + date, "Thành công"));
    }
}
