package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.request.CodCapacityHoldRequest;
import com.delivery.settlement_service.entity.Balance;
import com.delivery.settlement_service.entity.CodCapacityHold;
import com.delivery.settlement_service.entity.CodCapacityHoldStatus;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.exception.InsufficientBalanceException;
import com.delivery.settlement_service.repository.BalanceRepository;
import com.delivery.settlement_service.repository.CodCapacityHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class CodCapacityHoldService {

    private final BalanceRepository balanceRepository;
    private final CodCapacityHoldRepository holdRepository;

    @Transactional
    public List<CodCapacityHold> hold(CodCapacityHoldRequest request) {
        validate(request);
        List<CodCapacityHoldRequest.Item> items = request.getOffers().stream()
                .sorted(Comparator.comparing(CodCapacityHoldRequest.Item::getOfferId))
                .toList();

        Balance balance = balanceRepository.findByEntityIdAndEntityTypeForUpdate(
                        request.getShipperId(), EntityType.SHIPPER)
                .orElseThrow(() -> new InsufficientBalanceException("Shipper deposit balance not found"));
        BigDecimal reserved = balance.getReservedDepositBalance() == null
                ? BigDecimal.ZERO : balance.getReservedDepositBalance();
        LocalDateTime now = LocalDateTime.now();

        List<CodCapacityHold> existing = new ArrayList<>();
        BigDecimal requested = BigDecimal.ZERO;
        for (CodCapacityHoldRequest.Item item : items) {
            String key = idempotencyKey(request, item);
            CodCapacityHold replay = holdRepository.findByIdempotencyKeyForUpdate(key).orElse(null);
            if (replay != null) {
                if (replay.getStatus() != CodCapacityHoldStatus.HELD
                        || (replay.getExpiresAt() != null && !replay.getExpiresAt().isAfter(now))) {
                    throw new IllegalStateException("COD hold idempotency key is no longer reusable");
                }
                existing.add(replay);
            } else {
                requested = requested.add(item.getAmount());
            }
        }
        if (balance.getDepositBalance().subtract(reserved).compareTo(requested) < 0) {
            throw new InsufficientBalanceException("Insufficient COD capacity for batch hold");
        }

        List<CodCapacityHold> result = new ArrayList<>(existing);
        for (CodCapacityHoldRequest.Item item : items) {
            String key = idempotencyKey(request, item);
            if (holdRepository.findByIdempotencyKeyForUpdate(key).isPresent()) continue;
            CodCapacityHold hold = new CodCapacityHold();
            hold.setHoldId(item.getHoldId() == null ? UUID.randomUUID() : item.getHoldId());
            hold.setOfferId(item.getOfferId());
            hold.setOrderId(item.getOrderId());
            hold.setDeliveryId(item.getDeliveryId());
            hold.setShipperId(request.getShipperId());
            hold.setMatchingSessionId(request.getMatchingSessionId());
            hold.setWaveId(request.getWaveId());
            hold.setAmount(item.getAmount());
            hold.setStatus(CodCapacityHoldStatus.HELD);
            hold.setExpiresAt(item.getExpiresAt());
            hold.setEventId(request.getEventId());
            hold.setIdempotencyKey(key);
            hold.setCreatedAt(now);
            result.add(holdRepository.save(hold));
        }
        balance.setReservedDepositBalance(reserved.add(requested));
        balanceRepository.save(balance);
        return result;
    }

    @Transactional
    public CodCapacityHold transition(UUID holdId, CodCapacityHoldStatus target) {
        CodCapacityHold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new IllegalArgumentException("COD hold not found"));
        if (hold.getStatus() == target) return hold;
        if (hold.getStatus() == CodCapacityHoldStatus.HELD
                && target != CodCapacityHoldStatus.COMMITTED
                && target != CodCapacityHoldStatus.RELEASED
                && target != CodCapacityHoldStatus.EXPIRED) {
            throw new IllegalStateException("Invalid COD hold transition");
        }
        if (hold.getStatus() == CodCapacityHoldStatus.COMMITTED
                && target != CodCapacityHoldStatus.CONSUMED
                && target != CodCapacityHoldStatus.RELEASED) {
            throw new IllegalStateException("Committed COD hold can only be released or consumed");
        }

        if (hold.getStatus() == CodCapacityHoldStatus.HELD
                && target == CodCapacityHoldStatus.COMMITTED
                && hold.getExpiresAt() != null
                && !hold.getExpiresAt().isAfter(LocalDateTime.now())) {
            releaseReservedCapacity(hold);
            hold.setReleasedAt(LocalDateTime.now());
            hold.setStatus(CodCapacityHoldStatus.EXPIRED);
            return holdRepository.save(hold);
        }

        if (target == CodCapacityHoldStatus.RELEASED || target == CodCapacityHoldStatus.EXPIRED) {
            releaseReservedCapacity(hold);
            hold.setReleasedAt(LocalDateTime.now());
        } else if (target == CodCapacityHoldStatus.COMMITTED) {
            hold.setCommittedAt(LocalDateTime.now());
        } else if (target == CodCapacityHoldStatus.CONSUMED) {
            hold.setConsumedAt(LocalDateTime.now());
            releaseReservedCapacity(hold);
        }
        hold.setStatus(target);
        return holdRepository.save(hold);
    }

    @Transactional
    public void consumeForDelivery(Long deliveryId) {
        if (deliveryId == null || deliveryId <= 0) return;
        for (CodCapacityHold hold : holdRepository.findActiveByDeliveryIdForUpdate(deliveryId)) {
            if (hold.getStatus() == CodCapacityHoldStatus.HELD) {
                hold.setCommittedAt(LocalDateTime.now());
                releaseReservedCapacity(hold);
                hold.setConsumedAt(LocalDateTime.now());
                hold.setStatus(CodCapacityHoldStatus.CONSUMED);
                holdRepository.save(hold);
            } else if (hold.getStatus() == CodCapacityHoldStatus.COMMITTED) {
                transition(hold.getHoldId(), CodCapacityHoldStatus.CONSUMED);
            }
        }
    }

    @Scheduled(fixedDelayString = "${settlement.cod-hold.expiry-scan-ms:1000}")
    @Transactional
    public void expireDueHolds() {
        holdRepository.findExpiredHeldForUpdate(LocalDateTime.now(), PageRequest.of(0, 200))
                .forEach(hold -> transition(hold.getHoldId(), CodCapacityHoldStatus.EXPIRED));
    }

    private void releaseReservedCapacity(CodCapacityHold hold) {
        Balance balance = balanceRepository.findByEntityIdAndEntityTypeForUpdate(
                        hold.getShipperId(), EntityType.SHIPPER)
                .orElseThrow(() -> new IllegalStateException("Shipper balance missing for COD hold"));
        BigDecimal reserved = balance.getReservedDepositBalance() == null
                ? BigDecimal.ZERO : balance.getReservedDepositBalance();
        balance.setReservedDepositBalance(reserved.subtract(hold.getAmount()).max(BigDecimal.ZERO));
        balanceRepository.save(balance);
    }

    private String idempotencyKey(CodCapacityHoldRequest request, CodCapacityHoldRequest.Item item) {
        return request.getMatchingSessionId() + ":" + request.getWaveId() + ":" + item.getOfferId();
    }

    private void validate(CodCapacityHoldRequest request) {
        if (request == null || request.getEventId() == null || request.getShipperId() == null
                || request.getShipperId() <= 0 || request.getMatchingSessionId() == null
                || request.getOffers() == null || request.getOffers().isEmpty()
                || request.getOffers().size() > 3) {
            throw new IllegalArgumentException("Invalid COD capacity hold request");
        }
        for (CodCapacityHoldRequest.Item item : request.getOffers()) {
            if (item.getOfferId() == null || item.getOrderId() == null || item.getDeliveryId() == null
                    || item.getAmount() == null || item.getAmount().signum() <= 0
                    || item.getExpiresAt() == null) {
                throw new IllegalArgumentException("Invalid COD capacity hold item");
            }
        }
    }
}
