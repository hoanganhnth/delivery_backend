package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.FlashSaleReservationResponse;
import com.delivery.flashsale_service.dto.FlashSaleQuoteRequest;
import com.delivery.flashsale_service.dto.FlashSaleQuoteResponse;
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

import com.delivery.flashsale.application.StockUseCases;
import com.delivery.flashsale.application.api.StockPort;
import com.delivery.flashsale.domain.FlashSaleInputs;
import com.delivery.flashsale.domain.FlashSaleReservationPolicy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
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

    private StockUseCases<FlashSaleReservationResponse, FlashSaleQuoteResponse> useCases() {
        return new StockUseCases<>(new PersistenceAdapter());
    }
    @Transactional(readOnly = true)
    public FlashSaleQuoteResponse quote(FlashSaleQuoteRequest request) { return useCases().quote(request); }
    @Transactional
    public FlashSaleReservationResponse reserveStock(FlashSaleReservationRequest request) { return useCases().reserve(request); }
    @Transactional
    public FlashSaleReservationResponse commit(UUID id, Long orderId) { return useCases().commit(id, orderId); }
    @Transactional
    public FlashSaleReservationResponse release(UUID id, Long orderId) { return useCases().release(id, orderId); }
    @Transactional
    public int expireReservations() { return useCases().expire(); }

    private final class PersistenceAdapter implements StockPort<FlashSaleReservationResponse, FlashSaleQuoteResponse> {
        public LocalTime time() { return LocalTime.now(); }
        public LocalDateTime now() { return LocalDateTime.now(); }
        public boolean principalEnforced() { return principalOwnershipEnforced; }
        public void legacyFallback() { legacyReservationFallback().increment(); }
        public Optional<Reservation> find(UUID id) { return reservationRepository.findById(id).map(ReservationView::new); }
        public Optional<Reservation> findOrder(Long orderId) { return reservationRepository.findByOrderId(orderId).map(ReservationView::new); }
        public Optional<Reservation> lock(UUID id) { return reservationRepository.findByIdForUpdate(id).map(ReservationView::new); }
        public List<Item> items(Collection<Long> ids, boolean lock) {
            List<FlashSaleItem> rows = lock ? itemRepository.findAllByIdForUpdate(ids) : itemRepository.findAllById(ids);
            return rows.stream().map(row -> (Item) new ItemView(row)).toList();
        }
        public Reservation create(FlashSaleInputs.Reservation request, FlashSaleReservationPolicy.State state, LocalDateTime createdAt, LocalDateTime expiresAt) {
            return new ReservationView(FlashSaleReservation.builder()
                    .reservationId(request.getReservationId()).orderId(request.getOrderId())
                    .userId(request.getUserId()).userPrincipalId(request.getUserPrincipalId()).restaurantId(request.getRestaurantId())
                    .state(FlashSaleReservation.State.valueOf(state.name())).expiresAt(expiresAt)
                    .createdAt(createdAt).updatedAt(createdAt).build());
        }
        public void saveAndFlush(Reservation reservation) { reservationRepository.saveAndFlush(((ReservationView) reservation).row()); }
        public void enqueue(Reservation reservation) { outboxService.enqueue(((ReservationView) reservation).row()); }
        public FlashSaleReservationResponse response(Reservation reservation) { return FlashSaleReservationResponse.from(((ReservationView) reservation).row()); }
        public FlashSaleQuoteResponse quote(Long restaurantId, List<Line> lines) {
            return FlashSaleQuoteResponse.builder().restaurantId(restaurantId).items(lines.stream().map(line ->
                    FlashSaleQuoteResponse.Line.builder().flashSaleItemId(line.itemId()).menuItemId(line.menuItemId())
                            .quantity(line.quantity()).unitPrice(line.price()).build()).toList()).build();
        }
        public List<UUID> due(LocalDateTime now) {
            return reservationRepository.findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                    FlashSaleReservation.State.RESERVED, now).stream().map(FlashSaleReservation::getReservationId).toList();
        }
    }

    private record ItemView(FlashSaleItem row) implements StockPort.Item {
        public boolean deleted() { return row.getDeletedAt() != null; }
        public Long restaurantId() { return row.getRestaurantId(); }
        public boolean approved() { return row.getStatus() == FlashSaleItem.ItemStatus.APPROVED; }
        public boolean campaignActive() { return row.getCampaign().getStatus() == com.delivery.flashsale_service.entity.FlashSaleCampaign.CampaignStatus.ACTIVE; }
        public LocalTime campaignStartTime() { return row.getCampaign().getStartTime(); }
        public LocalTime campaignEndTime() { return row.getCampaign().getEndTime(); }
        public Integer stockQuantity() { return row.getStockQuantity(); }
        public Integer soldQuantity() { return row.getSoldQuantity(); }
        public void soldQuantity(int value) { row.setSoldQuantity(value); }
        public Long id() { return row.getId(); }
        public Long menuItemId() { return row.getMenuItemId(); }
        public BigDecimal price() { return row.getFlashSalePrice(); }
    }

    private record ReservationView(FlashSaleReservation row) implements StockPort.Reservation {
        public UUID id() { return row.getReservationId(); }
        public Long orderId() { return row.getOrderId(); }
        public FlashSaleReservationPolicy.State state() { return row.getState() == null ? null : FlashSaleReservationPolicy.State.valueOf(row.getState().name()); }
        public void state(FlashSaleReservationPolicy.State state) { row.setState(FlashSaleReservation.State.valueOf(state.name())); }
        public LocalDateTime expiresAt() { return row.getExpiresAt(); }
        public List<StockPort.Line> lines() { return row.getLines().stream().map(line -> new StockPort.Line(
                line.getFlashSaleItemId(), line.getMenuItemId(), line.getQuantity(), line.getUnitPrice())).toList(); }
        public void addLine(StockPort.Line line) { row.getLines().add(FlashSaleReservationLine.builder().reservation(row)
                .flashSaleItemId(line.itemId()).menuItemId(line.menuItemId()).quantity(line.quantity()).unitPrice(line.price()).build()); }
        public FlashSaleReservationPolicy.Identity identity() {
            return new FlashSaleReservationPolicy.Identity(row.getReservationId(), row.getOrderId(), row.getUserId(),
                    row.getUserPrincipalId(), row.getRestaurantId(), row.getLines().stream().collect(Collectors.toMap(
                            FlashSaleReservationLine::getFlashSaleItemId, FlashSaleReservationLine::getQuantity)));
        }
    }

    private Counter legacyReservationFallback() {
        return Counter.builder("delivery.identity.legacy.fallback")
                .tag("service", "flashsale").tag("surface", "reservation")
                .register(meterRegistry);
    }
}
