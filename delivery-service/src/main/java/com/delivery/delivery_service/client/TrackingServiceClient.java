package com.delivery.delivery_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * ✅ HTTP Client gọi tracking-service để quản lý trạng thái busy/available của shipper.
 * Khi shipper nhận đơn → markBusy → tracking-service loại shipper ra khỏi kết quả nearby.
 * Khi shipper hoàn thành/huỷ đơn → markAvailable → tracking-service cho shipper quay lại pool.
 */
@Slf4j
@Component
public class TrackingServiceClient {

    private final RestTemplate restTemplate;
    private final String trackingServiceUrl;

    public TrackingServiceClient(
            @Value("${services.tracking.url:http://localhost:8090}") String trackingServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.trackingServiceUrl = trackingServiceUrl;
    }

    /**
     * Đánh dấu shipper đang bận (đã nhận đơn)
     */
    public void markShipperBusy(Long shipperId) {
        try {
            String url = trackingServiceUrl + "/api/shipper-locations/" + shipperId + "/busy";
            restTemplate.put(url, null);
            log.info("🔴 [TrackingClient] Marked shipper {} as BUSY", shipperId);
        } catch (Exception e) {
            log.error("💥 [TrackingClient] Failed to mark shipper {} as busy: {}", shipperId, e.getMessage());
            // Non-blocking: không fail delivery flow nếu tracking-service lỗi
        }
    }

    /**
     * Đánh dấu shipper rảnh (hoàn thành hoặc huỷ đơn)
     */
    public void markShipperAvailable(Long shipperId) {
        try {
            String url = trackingServiceUrl + "/api/shipper-locations/" + shipperId + "/busy";
            restTemplate.delete(url);
            log.info("🟢 [TrackingClient] Marked shipper {} as AVAILABLE", shipperId);
        } catch (Exception e) {
            log.error("💥 [TrackingClient] Failed to mark shipper {} as available: {}", shipperId, e.getMessage());
            // Non-blocking: không fail delivery flow nếu tracking-service lỗi
        }
    }
}
