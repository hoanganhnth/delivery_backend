package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.FlashSaleReservationResponse;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.dto.FlashSaleQuoteRequest;
import com.delivery.flashsale_service.dto.FlashSaleQuoteResponse;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.entity.FlashSaleReservationLine;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.flashsale.checkout-enabled", havingValue = "true")
public class FlashSaleStockService {
    private final FlashSaleItemRepository itemRepository;
    private final FlashSaleReservationRepository reservationRepository;
    private final FlashSaleOutboxService outboxService;
    private final MeterRegistry meterRegistry;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Transactional(readOnly = true)
    public FlashSaleQuoteResponse quote(FlashSaleQuoteRequest request) {
        if (request == null || request.getRestaurantId() == null || request.getItems() == null
                || request.getItems().isEmpty()) throw new IllegalArgumentException("Invalid flash-sale quote request");
        Map<Long, ReserveItemRequest> requested = request.getItems().stream().collect(Collectors.toMap(
                ReserveItemRequest::getFlashSaleItemId, Function.identity(),
                (left, right) -> { throw new IllegalArgumentException("Duplicate flashSaleItemId"); }, TreeMap::new));
        List<FlashSaleItem> items = itemRepository.findAllById(requested.keySet());
        if (items.size() != requested.size()) throw new IllegalArgumentException("Flash sale item not found");
        LocalTime now = LocalTime.now();
        List<FlashSaleQuoteResponse.Line> lines = items.stream().sorted(Comparator.comparing(FlashSaleItem::getId))
                .map(item -> {
                    ReserveItemRequest line = requested.get(item.getId());
                    validateAvailable(item, request.getRestaurantId(), line.getQuantity(), now);
                    return FlashSaleQuoteResponse.Line.builder().flashSaleItemId(item.getId())
                            .menuItemId(item.getMenuItemId()).quantity(line.getQuantity())
                            .unitPrice(item.getFlashSalePrice()).build();
                }).toList();
        return FlashSaleQuoteResponse.builder().restaurantId(request.getRestaurantId()).items(lines).build();
    }

    @Transactional
    public FlashSaleReservationResponse reserveStock(FlashSaleReservationRequest request) {
        validateRequest(request);
        if (!principalOwnershipEnforced && request.getUserPrincipalId() == null) {
            legacyReservationFallback().increment();
        }
        Optional<FlashSaleReservation> replay = reservationRepository.findById(request.getReservationId());
        if (replay.isPresent()) return exactReplay(replay.get(), request);
        Optional<FlashSaleReservation> sameOrder = reservationRepository.findByOrderId(request.getOrderId());
        if (sameOrder.isPresent()) return exactReplay(sameOrder.get(), request);

        Map<Long, ReserveItemRequest> requested = request.getItems().stream().collect(Collectors.toMap(
                ReserveItemRequest::getFlashSaleItemId, Function.identity(),
                (left, right) -> { throw new IllegalArgumentException("Duplicate flashSaleItemId"); },
                TreeMap::new));
        List<FlashSaleItem> items = itemRepository.findAllByIdForUpdate(requested.keySet());
        if (items.size() != requested.size()) throw new IllegalArgumentException("Flash sale item not found");

        LocalTime now = LocalTime.now();
        LocalDateTime createdAt = LocalDateTime.now();
        FlashSaleReservation reservation = FlashSaleReservation.builder()
                .reservationId(request.getReservationId()).orderId(request.getOrderId())
                .userId(request.getUserId()).userPrincipalId(request.getUserPrincipalId()).restaurantId(request.getRestaurantId())
                .state(FlashSaleReservation.State.RESERVED).expiresAt(createdAt.plusMinutes(15))
                .createdAt(createdAt).updatedAt(createdAt).build();

        for (FlashSaleItem item : items) {
            ReserveItemRequest lineRequest = requested.get(item.getId());
            validateAvailable(item, request.getRestaurantId(), lineRequest.getQuantity(), now);
            reservation.getLines().add(FlashSaleReservationLine.builder()
                    .reservation(reservation).flashSaleItemId(item.getId()).menuItemId(item.getMenuItemId())
                    .quantity(lineRequest.getQuantity()).unitPrice(item.getFlashSalePrice()).build());
        }
        // No write occurs until every line has passed validation. The locked rows and this
        // transaction make the whole cart one all-or-nothing stock operation.
        for (FlashSaleItem item : items) {
            item.setSoldQuantity(item.getSoldQuantity() + requested.get(item.getId()).getQuantity());
        }
        reservationRepository.saveAndFlush(reservation);
        outboxService.enqueue(reservation);
        return FlashSaleReservationResponse.from(reservation);
    }

    @Transactional
    public FlashSaleReservationResponse commit(UUID reservationId, Long orderId) {
        FlashSaleReservation reservation = locked(reservationId, orderId);
        if (reservation.getState() == FlashSaleReservation.State.RESERVED
                && !LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
            releaseCapacity(reservation, FlashSaleReservation.State.EXPIRED);
        } else if (reservation.getState() == FlashSaleReservation.State.RESERVED) {
            reservation.setState(FlashSaleReservation.State.COMMITTED);
            outboxService.enqueue(reservation);
        }
        return FlashSaleReservationResponse.from(reservation);
    }

    @Transactional
    public FlashSaleReservationResponse release(UUID reservationId, Long orderId) {
        FlashSaleReservation reservation = locked(reservationId, orderId);
        if (reservation.getState() == FlashSaleReservation.State.RESERVED
                || reservation.getState() == FlashSaleReservation.State.COMMITTED) {
            releaseCapacity(reservation, FlashSaleReservation.State.RELEASED);
        }
        return FlashSaleReservationResponse.from(reservation);
    }

    @Transactional
    public int expireReservations() {
        List<FlashSaleReservation> due = reservationRepository
                .findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        FlashSaleReservation.State.RESERVED, LocalDateTime.now());
        int expired = 0;
        for (FlashSaleReservation candidate : due) {
            FlashSaleReservation reservation = reservationRepository
                    .findByIdForUpdate(candidate.getReservationId()).orElse(null);
            if (reservation != null && reservation.getState() == FlashSaleReservation.State.RESERVED
                    && !LocalDateTime.now().isBefore(reservation.getExpiresAt())) {
                releaseCapacity(reservation, FlashSaleReservation.State.EXPIRED);
                expired++;
            }
        }
        return expired;
    }

    private void releaseCapacity(FlashSaleReservation reservation, FlashSaleReservation.State terminal) {
        List<Long> ids = reservation.getLines().stream().map(FlashSaleReservationLine::getFlashSaleItemId)
                .sorted().toList();
        Map<Long, FlashSaleItem> items = itemRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(FlashSaleItem::getId, Function.identity()));
        for (FlashSaleReservationLine line : reservation.getLines()) {
            FlashSaleItem item = items.get(line.getFlashSaleItemId());
            if (item == null || item.getSoldQuantity() < line.getQuantity()) {
                throw new IllegalStateException("Flash sale stock ledger is inconsistent");
            }
            item.setSoldQuantity(item.getSoldQuantity() - line.getQuantity());
        }
        reservation.setState(terminal);
        outboxService.enqueue(reservation);
    }

    private FlashSaleReservation locked(UUID reservationId, Long orderId) {
        if (reservationId == null || orderId == null || orderId <= 0)
            throw new IllegalArgumentException("reservationId and positive orderId are required");
        FlashSaleReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Flash sale reservation not found"));
        if (!reservation.getOrderId().equals(orderId))
            throw new IllegalArgumentException("reservationId is bound to another order");
        return reservation;
    }

    private FlashSaleReservationResponse exactReplay(FlashSaleReservation reservation,
                                                       FlashSaleReservationRequest request) {
        Map<Long, Integer> existing = reservation.getLines().stream().collect(Collectors.toMap(
                FlashSaleReservationLine::getFlashSaleItemId, FlashSaleReservationLine::getQuantity));
        Map<Long, Integer> incoming = request.getItems().stream().collect(Collectors.toMap(
                ReserveItemRequest::getFlashSaleItemId, ReserveItemRequest::getQuantity,
                (left, right) -> { throw new IllegalArgumentException("Duplicate flashSaleItemId"); }));
        if (!reservation.getReservationId().equals(request.getReservationId())
                || !reservation.getOrderId().equals(request.getOrderId())
                || !reservation.getUserId().equals(request.getUserId())
                || !java.util.Objects.equals(reservation.getUserPrincipalId(), request.getUserPrincipalId())
                || !reservation.getRestaurantId().equals(request.getRestaurantId())
                || !existing.equals(incoming)) {
            throw new IllegalArgumentException("Reservation replay payload does not match");
        }
        return FlashSaleReservationResponse.from(reservation);
    }

    private void validateAvailable(FlashSaleItem item, Long restaurantId, int quantity, LocalTime now) {
        if (!item.getRestaurantId().equals(restaurantId))
            throw new IllegalArgumentException("Flash sale item belongs to another restaurant");
        if (item.getStatus() != FlashSaleItem.ItemStatus.APPROVED)
            throw new IllegalArgumentException("Flash sale item is not approved");
        FlashSaleCampaign campaign = item.getCampaign();
        if (campaign.getStatus() != FlashSaleCampaign.CampaignStatus.ACTIVE
                || now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime()))
            throw new IllegalArgumentException("Flash sale campaign is not active");
        if (item.getStockQuantity() - item.getSoldQuantity() < quantity)
            throw new IllegalArgumentException("Out of stock for flash sale item " + item.getId());
    }

    private void validateRequest(FlashSaleReservationRequest request) {
        if (request == null || request.getReservationId() == null || request.getOrderId() == null
                || request.getOrderId() <= 0 || request.getUserId() == null || request.getUserId() <= 0
                || request.getRestaurantId() == null || request.getRestaurantId() <= 0
                || request.getItems() == null || request.getItems().isEmpty())
            throw new IllegalArgumentException("Invalid flash sale reservation request");
        if (principalOwnershipEnforced && (request.getUserPrincipalId() == null || request.getUserPrincipalId() <= 0)) {
            throw new IllegalArgumentException("userPrincipalId is required when principal ownership is enforced");
        }
    }

    private Counter legacyReservationFallback() {
        return Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "flashsale").tag("surface", "reservation")
                .register(meterRegistry);
    }
}
