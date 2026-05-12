package com.delivery.order_service.controller;

import com.delivery.order_service.common.constants.HttpHeaderConstants;
import com.delivery.order_service.dto.response.DashboardStats;
import com.delivery.order_service.payload.BaseResponse;
import com.delivery.order_service.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard Statistics API
 *
 * Cung cấp thống kê cho:
 * - Admin: thống kê toàn bộ platform (doanh thu, đơn hàng, top nhà hàng...)
 * - Restaurant: thống kê cho 1 nhà hàng cụ thể (doanh thu, đơn hàng, top món...)
 *
 * Params:
 * - period: "month" | "quarter" | "year"
 * - year: năm cần thống kê (mặc định năm hiện tại)
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * ✅ GET /api/dashboard/admin?period=month&year=2026
     * Thống kê toàn bộ platform cho Admin
     */
    @GetMapping("/admin")
    public ResponseEntity<BaseResponse<DashboardStats.AdminDashboardResponse>> getAdminDashboard(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        DashboardStats.AdminDashboardResponse response = dashboardService.getAdminDashboard(period, year);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê Admin thành công"));
    }

    /**
     * ✅ GET /api/dashboard/restaurant/{restaurantId}?period=month&year=2026
     * Thống kê cho 1 nhà hàng cụ thể
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<BaseResponse<DashboardStats.RestaurantDashboardResponse>> getRestaurantDashboard(
            @PathVariable Long restaurantId,
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        DashboardStats.RestaurantDashboardResponse response = dashboardService.getRestaurantDashboard(
            restaurantId, period, year
        );
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê nhà hàng thành công"));
    }

    /**
     * ✅ GET /api/dashboard/my-restaurant?period=month&year=2026
     * Thống kê cho nhà hàng của chính mình (dùng X-User-Id làm restaurantId)
     */
    @GetMapping("/my-restaurant")
    public ResponseEntity<BaseResponse<DashboardStats.RestaurantDashboardResponse>> getMyRestaurantDashboard(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long restaurantId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        // Nếu không truyền restaurantId, dùng userId (owner = restaurant creator)
        Long targetRestaurantId = restaurantId != null ? restaurantId : userId;
        DashboardStats.RestaurantDashboardResponse response = dashboardService.getRestaurantDashboard(
            targetRestaurantId, period, year
        );
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thống kê nhà hàng thành công"));
    }
}
