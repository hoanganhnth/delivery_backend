package com.delivery.match_service.service;

import com.delivery.match_service.dispatch.BoundedDispatchOptimizer;
import com.delivery.match_service.dispatch.DispatchBundleCandidate;
import com.delivery.match_service.dto.event.ShipperFoundEvent;
import com.delivery.match_service.entity.DispatchPoolItem;
import com.delivery.match_service.entity.DispatchRound;
import com.delivery.match_service.entity.MatchOutboxEvent;
import com.delivery.match_service.repository.DispatchPoolItemRepository;
import com.delivery.match_service.repository.DispatchRoundRepository;
import com.delivery.match_service.repository.MatchOutboxEventRepository;
import com.delivery.match_service.repository.MatchRedisGeoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executes a due round and commits one immutable proposal to the Match outbox. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchRoundExecutionService {

    private final DispatchRoundRepository roundRepository;
    private final DispatchPoolItemRepository poolRepository;
    private final MatchRedisGeoRepository geoRepository;
    private final MatchOutboxEventRepository outboxRepository;
    private final SettlementEligibilityClient settlementEligibilityClient;
    private final com.delivery.match_service.config.MatchingBatchProperties properties;
    private final ObjectMapper objectMapper;
    private final RoutingClient routingClient;
    private final Clock clock = Clock.systemDefaultZone();
    private final BoundedDispatchOptimizer optimizer = new BoundedDispatchOptimizer();

    @Transactional
    public void executeDueRounds() {
        if (!properties.isEnabled() || !properties.isSchedulerEnabled()) return;
        LocalDateTime now = LocalDateTime.now(clock);
        roundRepository.findOpenDueForUpdate(now, PageRequest.of(0, 100))
                .forEach(this::execute);
    }

    @Transactional
    public void execute(DispatchRound round) {
        if (round == null || round.getState() != DispatchRound.State.OPEN) return;
        List<DispatchPoolItem> items = poolRepository.findByClaimedRoundId(round.getDispatchRoundId());
        if (items.isEmpty()) {
            close(round, DispatchRound.State.EXPIRED);
            return;
        }
        round.setState(DispatchRound.State.RUNNING);
        round.setShipperCount(0);
        round.setUpdatedAt(LocalDateTime.now(clock));
        roundRepository.save(round);

        List<DispatchBundleCandidate> candidates = candidates(items);
        List<DispatchBundleCandidate> selected = optimizer.optimize(
                candidates, Math.min(Math.min(properties.getMaxShippersPerRound(),
                        properties.getMaxShippersPerWave()), 100));
        if (selected.isEmpty()) {
            items.forEach(item -> requeue(item, now()));
            close(round, DispatchRound.State.REQUEUED);
            return;
        }

        int assigned = 0;
        Set<UUID> assignedPoolItemIds = new HashSet<>();
        for (DispatchBundleCandidate candidate : selected) {
            UUID batchId = UUID.nameUUIDFromBytes(("dispatch-batch:" + round.getDispatchRoundId()
                    + ":" + candidate.shipperId() + ":" + candidate.bundleId()).getBytes(StandardCharsets.UTF_8));
            List<DispatchPoolItem> batchItems = candidate.poolItemIds().stream()
                    .map(id -> items.stream().filter(item -> item.getPoolItemId().equals(id)).findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull).toList();
            List<SettlementEligibilityClient.CodCapacityHoldRef> holds = createHolds(batchId, candidate.shipperId(), batchItems);
            if (holds == null) {
                batchItems.forEach(item -> requeue(item, now()));
                continue;
            }
            if (!geoRepository.tryReserveShipperBatchOffer(candidate.shipperId(),
                    batchItems.stream().map(DispatchPoolItem::getDeliveryId).toList(),
                    batchId, batchItems.get(0).getMatchingSessionId(), 180)) {
                releaseHolds(holds);
                batchItems.forEach(item -> requeue(item, now()));
                continue;
            }
            try {
                for (ShipperFoundEvent event : toBatchEvents(batchId, candidate.shipperId(), batchItems,
                        candidate.orderedPoolItemIds(),
                        holds.stream().map(SettlementEligibilityClient.CodCapacityHoldRef::holdId).toList())) {
                    persistOutbox(batchId, round, event);
                }
            } catch (RuntimeException failure) {
                geoRepository.releaseShipperBatchOffer(candidate.shipperId(),
                        batchItems.stream().map(DispatchPoolItem::getDeliveryId).toList(),
                        batchId, batchItems.get(0).getMatchingSessionId());
                releaseHolds(holds);
                throw failure;
            }
            batchItems.forEach(item -> {
                item.setState(DispatchPoolItem.State.ASSIGNED);
                item.setWaveNumber(item.getWaveNumber() == 0 ? 1 : item.getWaveNumber());
                item.setUpdatedAt(now());
            });
            poolRepository.saveAll(batchItems);
            assignedPoolItemIds.addAll(candidate.poolItemIds());
            assigned++;
        }

        items.stream().filter(item -> !assignedPoolItemIds.contains(item.getPoolItemId())
                        && item.getState() == DispatchPoolItem.State.CLAIMED)
                .forEach(item -> requeue(item, now()));
        poolRepository.saveAll(items);
        round.setShipperCount(assigned);
        close(round, assigned > 0 ? DispatchRound.State.COMMITTED : DispatchRound.State.REQUEUED);
    }

    private List<DispatchBundleCandidate> candidates(List<DispatchPoolItem> items) {
        Map<Long, Set<UUID>> shipperOrders = new HashMap<>();
        Map<Long, MatchRedisGeoRepository.NearbyShipperResult> locations = new HashMap<>();
        Map<UUID, DispatchPoolItem> itemsById = items.stream()
                .collect(java.util.stream.Collectors.toMap(DispatchPoolItem::getPoolItemId, item -> item));
        for (DispatchPoolItem item : items) {
            if (item.getWaveNumber() >= Math.max(1, properties.getMaxWaves())) continue;
            if (!"COD".equalsIgnoreCase(item.getPaymentMethod())
                    || item.getPickupLat() == null || item.getPickupLng() == null
                    || item.getDeliveryLat() == null || item.getDeliveryLng() == null) continue;
            List<MatchRedisGeoRepository.NearbyShipperResult> nearby = geoRepository.findNearbyShippers(
                    item.getPickupLat(), item.getPickupLng(), 5.0, properties.getMaxShippersPerRound());
            for (MatchRedisGeoRepository.NearbyShipperResult shipper : nearby) {
                if (!codEligible(shipper.shipperId, item.getTotalPrice())) continue;
                locations.putIfAbsent(shipper.shipperId, shipper);
                shipperOrders.computeIfAbsent(shipper.shipperId, ignored -> new HashSet<>()).add(item.getPoolItemId());
            }
        }

        List<DispatchBundleCandidate> result = new ArrayList<>();
        for (Map.Entry<Long, Set<UUID>> entry : shipperOrders.entrySet()) {
            List<UUID> orderIds = entry.getValue().stream().sorted().toList();
            MatchRedisGeoRepository.NearbyShipperResult shipper = locations.get(entry.getKey());
            List<UUID> seedOrderIds = orderIds.stream()
                    .sorted(Comparator.comparingDouble((UUID id) -> distanceKm(shipper.latitude, shipper.longitude,
                            itemsById.get(id).getPickupLat(), itemsById.get(id).getPickupLng()))
                            .thenComparing(UUID::toString))
                    .limit(Math.max(1, properties.getBundleSeedOrdersPerShipper()))
                    .toList();
            int[] emitted = {0};
            for (int size = 1; size <= Math.min(3, orderIds.size()); size++) {
                List<UUID> source = size == 1 ? orderIds : seedOrderIds;
                combinations(source, size, 0, new ArrayList<>(), combo -> {
                    if (emitted[0] >= Math.max(1, properties.getMaxBundleCandidatesPerShipper())) return;
                    List<DispatchPoolItem> comboItems = combo.stream()
                            .map(itemsById::get)
                            .filter(java.util.Objects::nonNull).toList();
                    if (!feasible(comboItems)) return;
                    RoutingClient.RoutePlan routePlan = routingClient.planRoute(
                            shipper.latitude, shipper.longitude, comboItems);
                    long routeSeconds = routePlan.durationSeconds();
                    long incremental = Math.max(0, routeSeconds - soloRouteSeconds(locations.get(entry.getKey()), comboItems));
                    if (incremental > properties.getMaxEtaDetourSeconds()) return;
                    List<UUID> orderedPoolItemIds = routePlan.orderedItems().stream()
                            .map(DispatchPoolItem::getPoolItemId).toList();
                    result.add(new DispatchBundleCandidate(UUID.nameUUIDFromBytes(
                            ("bundle:" + entry.getKey() + ":" + combo).getBytes(StandardCharsets.UTF_8)),
                            entry.getKey(), combo, orderedPoolItemIds, routeSeconds, incremental,
                            routeSeconds * 1_000L + incremental * 100L));
                    emitted[0]++;
                });
            }
        }
        return result;
    }

    private boolean codEligible(Long shipperId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return false;
        try {
            return Boolean.TRUE.equals(settlementEligibilityClient.isCodEligible(shipperId, amount)
                    .block(Duration.ofSeconds(2)));
        } catch (RuntimeException ex) {
            log.warn("COD eligibility unavailable for batch candidate shipper={}: {}", shipperId, ex.getMessage());
            return false;
        }
    }

    private List<SettlementEligibilityClient.CodCapacityHoldRef> createHolds(
            UUID batchId, Long shipperId, List<DispatchPoolItem> items) {
        try {
            LocalDateTime expiresAt = now().plusSeconds(180);
            List<SettlementEligibilityClient.CodCapacityHoldRequestItem> requests = items.stream()
                    .map(item -> new SettlementEligibilityClient.CodCapacityHoldRequestItem(
                            UUID.nameUUIDFromBytes(("cod-hold:" + batchId + ":" + item.getDeliveryId())
                                    .getBytes(StandardCharsets.UTF_8)),
                            UUID.nameUUIDFromBytes(("cod-offer:" + batchId + ":" + item.getDeliveryId())
                                    .getBytes(StandardCharsets.UTF_8)),
                            item.getOrderId(), item.getDeliveryId(), item.getTotalPrice(), expiresAt))
                    .toList();
            List<SettlementEligibilityClient.CodCapacityHoldRef> holds =
                    settlementEligibilityClient.createCodCapacityHolds(
                            shipperId, items.get(0).getMatchingSessionId(), batchId, batchId, requests)
                            .block(Duration.ofSeconds(2));
            if (holds == null || holds.size() != items.size()) {
                log.warn("Settlement returned incomplete COD holds for batch {}", batchId);
                return null;
            }
            return holds;
        } catch (RuntimeException failure) {
            log.warn("Unable to create COD holds for batch {}: {}", batchId, failure.getMessage());
            return null;
        }
    }

    private void releaseHolds(List<SettlementEligibilityClient.CodCapacityHoldRef> holds) {
        holds.forEach(hold -> {
            try {
                settlementEligibilityClient.transitionCodCapacityHold(hold.holdId(), "RELEASED")
                        .block(Duration.ofSeconds(2));
            } catch (RuntimeException failure) {
                log.error("COD hold release compensation failed for {}: {}", hold.holdId(), failure.getMessage());
            }
        });
    }

    private boolean feasible(List<DispatchPoolItem> items) {
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                if (distanceKm(items.get(i).getPickupLat(), items.get(i).getPickupLng(),
                        items.get(j).getPickupLat(), items.get(j).getPickupLng()) > 2.0) return false;
            }
        }
        return true;
    }

    private long routeSeconds(MatchRedisGeoRepository.NearbyShipperResult shipper, List<DispatchPoolItem> items) {
        if (shipper == null) return Long.MAX_VALUE / 4;
        return routingClient.estimateRouteSeconds(shipper.latitude, shipper.longitude, items);
    }

    private long soloRouteSeconds(MatchRedisGeoRepository.NearbyShipperResult shipper, List<DispatchPoolItem> items) {
        return items.stream().mapToLong(item -> routeSeconds(shipper, List.of(item))).min().orElse(0);
    }

    private List<ShipperFoundEvent> toBatchEvents(UUID batchId, Long shipperId, List<DispatchPoolItem> items,
                                                   List<UUID> orderedPoolItemIds,
                                                   List<UUID> codHoldIds) {
        return items.stream().map(primary -> {
        ShipperFoundEvent event = new ShipperFoundEvent(primary.getDeliveryId(), primary.getOrderId(), List.of(
                new ShipperFoundEvent.ShipperMatchResult(shipperId, null, null, null, null, null, null, true)));
        event.setEventId(UUID.nameUUIDFromBytes(("shipper-found:" + batchId + ":" + primary.getDeliveryId())
                .getBytes(StandardCharsets.UTF_8)).toString());
        event.setMatchingSessionId(primary.getMatchingSessionId().toString());
        event.setFoundAt(LocalDateTime.now(clock));
        event.setWaitingTimeoutSeconds(Math.max(1, Math.min(properties.getWaveTimeoutSeconds(), 180)));
        event.setBatchOffer(true);
        event.setBatchId(batchId);
        event.setBatchWave(items.stream().mapToInt(DispatchPoolItem::getWaveNumber).max().orElse(0));
        event.setCodHoldIds(codHoldIds);
        Map<UUID, DispatchPoolItem> byId = items.stream()
                .collect(java.util.stream.Collectors.toMap(DispatchPoolItem::getPoolItemId, item -> item));
        List<DispatchPoolItem> orderedItemsCandidate = orderedPoolItemIds.stream()
                .map(byId::get).filter(java.util.Objects::nonNull).toList();
        if (orderedItemsCandidate.size() != items.size()) {
            orderedItemsCandidate = items.stream().sorted(Comparator.comparing(DispatchPoolItem::getOrderId,
                    Comparator.nullsLast(Long::compareTo))).toList();
        }
        final List<DispatchPoolItem> orderedItems = orderedItemsCandidate;
        int itemCount = orderedItems.size();
        // The route contract uses global stop positions. Keep pickups in the
        // first contiguous half and the matching drop-offs in the second half;
        // this guarantees pickupSequence < dropoffSequence for every item and
        // gives Delivery a deterministic 0..(2*n-1) snapshot.
        event.setBatchItems(java.util.stream.IntStream.range(0, itemCount)
                .mapToObj(index -> {
                    DispatchPoolItem item = orderedItems.get(index);
                    return new ShipperFoundEvent.BatchItem(item.getDeliveryId(), item.getOrderId(),
                            index, itemCount + index, item.getTotalPrice(), item.getMatchingSessionId());
                }).toList());
        return event;
        }).toList();
    }

    private void persistOutbox(UUID batchId, DispatchRound round, ShipperFoundEvent event) {
        try {
            MatchOutboxEvent outbox = new MatchOutboxEvent();
            outbox.setEventId(UUID.fromString(event.getEventId()));
            outbox.setCommandEventId(batchId);
            outbox.setAggregateId("batch:" + batchId);
            outbox.setEventType("SHIPPER_FOUND_BATCH");
            outbox.setTopic("shipper.found");
            outbox.setEventKey(batchId.toString());
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setStatus(MatchOutboxEvent.Status.PENDING);
            outbox.setAttempts(0);
            outbox.setNextAttemptAt(now());
            outbox.setCreatedAt(now());
            outboxRepository.save(outbox);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize batch shipper proposal", ex);
        }
    }

    private void requeue(DispatchPoolItem item, LocalDateTime now) {
        // REQUEUED is an audit outcome, but the same row must become eligible
        // for the next rolling round; the ready query intentionally consumes
        // WAITING only.
        item.setState(item.getWaveNumber() >= Math.max(1, properties.getMaxWaves())
                ? DispatchPoolItem.State.EXPIRED : DispatchPoolItem.State.WAITING);
        item.setClaimedRoundId(null);
        item.setEligibleAt(now.plusSeconds(1));
        item.setUpdatedAt(now);
    }

    private void close(DispatchRound round, DispatchRound.State state) {
        round.setState(state);
        round.setClosedAt(now());
        round.setUpdatedAt(now());
        roundRepository.save(round);
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    private double distanceKm(Double aLat, Double aLng, Double bLat, Double bLng) {
        if (aLat == null || aLng == null || bLat == null || bLng == null) return Double.MAX_VALUE;
        double dLat = Math.toRadians(bLat - aLat), dLng = Math.toRadians(bLng - aLng);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private interface CombinationConsumer { void accept(List<UUID> ids); }

    private void combinations(List<UUID> values, int size, int start, List<UUID> current, CombinationConsumer consumer) {
        if (current.size() == size) { consumer.accept(List.copyOf(current)); return; }
        for (int i = start; i <= values.size() - (size - current.size()); i++) {
            current.add(values.get(i));
            combinations(values, size, i + 1, current, consumer);
            current.remove(current.size() - 1);
        }
    }
}
