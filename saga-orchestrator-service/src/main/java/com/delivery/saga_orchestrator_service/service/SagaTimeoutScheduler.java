package com.delivery.saga_orchestrator_service.service;

import com.delivery.saga_orchestrator_service.entity.SagaInstance;
import com.delivery.saga_orchestrator_service.entity.SagaInstance.SagaStatus;
import com.delivery.saga_orchestrator_service.repository.SagaInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ✅ Saga Timeout Scheduler — Phát hiện saga bị "treo" và tự động compensation
 *
 * Kịch bản:
 * - Saga ở trạng thái STARTED nhưng không có delivery.created.result sau 2 phút
 * → TIMEOUT
 * - Saga ở trạng thái FINDING_SHIPPER quá 5 phút → TIMEOUT
 * - Saga ở trạng thái SHIPPER_FOUND quá 3 phút (shipper không accept) → TIMEOUT
 */
@Slf4j
@Component
public class SagaTimeoutScheduler {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaManager sagaManager;

    // Timeout thresholds (phút)
    private static final int STARTED_TIMEOUT_MINUTES = 2;
    private static final int FINDING_SHIPPER_TIMEOUT_MINUTES = 5;
    private static final int SHIPPER_FOUND_TIMEOUT_MINUTES = 3;

    public SagaTimeoutScheduler(SagaInstanceRepository sagaInstanceRepository,
            SagaManager sagaManager) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaManager = sagaManager;
    }

    /**
     * Chạy mỗi 30 giây — kiểm tra saga bị timeout
     */
    @Scheduled(fixedRate = 30000)
    public void checkTimeouts() {
        checkStuckSagas(SagaStatus.STARTED, STARTED_TIMEOUT_MINUTES, "Delivery creation timeout");
        checkStuckSagas(SagaStatus.DELIVERY_CREATED, FINDING_SHIPPER_TIMEOUT_MINUTES, "Find shipper command timeout");
        checkStuckSagas(SagaStatus.FINDING_SHIPPER, FINDING_SHIPPER_TIMEOUT_MINUTES, "Shipper search timeout");
        checkStuckSagas(SagaStatus.SHIPPER_FOUND, SHIPPER_FOUND_TIMEOUT_MINUTES, "Shipper acceptance timeout");
    }

    private void checkStuckSagas(SagaStatus status, int timeoutMinutes, String reason) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<SagaInstance> stuckSagas = sagaInstanceRepository.findByStatus(status);

        for (SagaInstance saga : stuckSagas) {
            if (saga.getUpdatedAt() != null && saga.getUpdatedAt().isBefore(cutoff)) {
                log.warn("⏰ [Saga] TIMEOUT — orderId={}, status={}, stuck since {}, reason={}",
                        saga.getOrderId(), status, saga.getUpdatedAt(), reason);

                // Gọi SagaManager để xử lý compensation
                String timeoutEvent = String.format(
                        "{\"orderId\":%d,\"reason\":\"%s\",\"timeout\":true,\"stuckStatus\":\"%s\"}",
                        saga.getOrderId(), reason, status);
                sagaManager.handleStepFailed("TIMEOUT_" + status.name(),
                        saga.getOrderId(), reason, timeoutEvent);
            }
        }
    }
}
