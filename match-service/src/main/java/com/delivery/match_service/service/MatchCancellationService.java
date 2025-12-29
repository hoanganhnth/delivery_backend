package com.delivery.match_service.service;

/**
 * Lưu/đọc cờ cancel matching theo deliveryId trong Redis.
 */
public interface MatchCancellationService {

    /**
     * Đánh dấu deliveryId đã bị cancel (stop matching).
     */
    void markCancelled(Long deliveryId);

    /**
     * Xoá cờ cancel khi bắt đầu matching mới (idempotent).
     */
    void clearCancelled(Long deliveryId);

    /**
     * Kiểm tra deliveryId có bị cancel không.
     */
    boolean isCancelled(Long deliveryId);
}
