package com.delivery.analytics_service.controller;

import com.delivery.analytics_service.dto.DashboardResponse;
import com.delivery.analytics_service.payload.BaseResponse;
import com.delivery.analytics_service.scheduler.StatsReconciliationJob;
import com.delivery.analytics_service.service.DashboardQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Dashboard Statistics API — Analytics Service
 *
 * Endpoints:
 *   GET /api/analytics/dashboard/admin?period=month&year=2026
 *   GET /api/analytics/dashboard/restaurant/{restaurantId}?period=month&year=2026
 *   GET /api/analytics/dashboard/my-restaurant?period=month&year=2026&restaurantId=5
 *   POST /api/analytics/reconcile?date=2026-05-12  (Manual re-computation)
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService queryService;
    private final StatsReconciliationJob reconciliationJob;

    /**
     * Admin Dashboard — Thống kê toàn bộ platform
     */
    @GetMapping("/dashboard/admin")
    public ResponseEntity<BaseResponse<DashboardResponse.AdminDashboard>> getAdminDashboard(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Role", required = false) String role) {

        DashboardResponse.AdminDashboard response = queryService.getAdminDashboard(period, year);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê Admin thành công"));
    }

    /**
     * Restaurant Dashboard — Thống kê cho 1 nhà hàng
     */
    @GetMapping("/dashboard/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<DashboardResponse.RestaurantDashboard>> getRestaurantDashboard(
            @PathVariable Long restaurantId,
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Role", required = false) String role) {

        DashboardResponse.RestaurantDashboard response = queryService.getRestaurantDashboard(
                restaurantId, period, year);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê nhà hàng thành công"));
    }

    /**
     * My Restaurant Dashboard — Thống kê cho nhà hàng của chính mình
     */
    @GetMapping("/dashboard/my-restaurant")
    public ResponseEntity<BaseResponse<DashboardResponse.RestaurantDashboard>> getMyRestaurantDashboard(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long restaurantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Role", required = false) String role) {

        Long targetId = restaurantId != null ? restaurantId : userId;
        DashboardResponse.RestaurantDashboard response = queryService.getRestaurantDashboard(
                targetId, period, year);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê nhà hàng thành công"));
    }

    /**
     * Manual Reconciliation — Tính toán lại thống kê cho 1 ngày cụ thể
     * Dùng khi cần sửa lỗi dữ liệu hoặc re-import
     */
    @PostMapping("/reconcile")
    public ResponseEntity<BaseResponse<String>> manualReconcile(
            @RequestParam String date) {
        LocalDate targetDate = LocalDate.parse(date);
        reconciliationJob.reconcileDate(targetDate);
        return ResponseEntity.ok(new BaseResponse<>(1, "Reconciliation completed for " + date, "Thành công"));
    }
}
