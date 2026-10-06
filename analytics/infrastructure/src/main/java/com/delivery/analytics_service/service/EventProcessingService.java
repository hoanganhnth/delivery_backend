package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.entity.DailyItemSales;
import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.entity.DailyRevenueStats;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyItemSalesRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import com.delivery.analytics.domain.*;
import com.delivery.analytics.domain.SnapshotDecisions.Item;
import com.delivery.analytics.application.IngestionService;
import com.delivery.analytics.applicationapi.IngestionUseCase;
import com.delivery.analytics.applicationapi.IngestionPorts;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Service xử lý events từ Kafka và cập nhật bảng thống kê
 *
 * Cơ chế:
 * 1. Mỗi event từ Kafka → lưu vào bảng AnalyticsEvent (raw log)
 * 2. Đồng thời cập nhật (upsert) bảng DailyOrderStats / DailyRevenueStats
 * 3. Scheduled Job chạy hàng đêm sẽ re-compute từ raw events để đảm bảo accuracy
 */
@Service
@Slf4j
public class EventProcessingService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AnalyticsEventRepository eventRepo;
    private final DailyOrderStatsRepository orderStatsRepo;
    private final DailyRevenueStatsRepository revenueStatsRepo;
    /** Null only in legacy focused fixtures; Spring production wiring is explicit. */
    private final DailyItemSalesRepository itemSalesRepo;

    @Autowired
    public EventProcessingService(AnalyticsEventRepository eventRepo,
                                  DailyOrderStatsRepository orderStatsRepo,
                                  DailyRevenueStatsRepository revenueStatsRepo,
                                  DailyItemSalesRepository itemSalesRepo) {
        this.eventRepo = eventRepo;
        this.orderStatsRepo = orderStatsRepo;
        this.revenueStatsRepo = revenueStatsRepo;
        this.itemSalesRepo = itemSalesRepo;
    }

    /** Compatibility constructor for pre-item-projection unit fixtures. */
    public EventProcessingService(AnalyticsEventRepository eventRepo,
                                  DailyOrderStatsRepository orderStatsRepo,
                                  DailyRevenueStatsRepository revenueStatsRepo) {
        this(eventRepo, orderStatsRepo, revenueStatsRepo, null);
    }

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    private final IngestionUseCase ingestion = new IngestionService(
            this::claim, new IngestionPorts.Payloads() {
                public LocalDate eventDate(String payload, LocalDate fallback) {
                    return EventProcessingService.this.eventDate(payload, fallback);
                }
                public List<Item> items(String payload) { return AnalyticsItemSnapshotParser.parse(readJson(payload)); }
            }, new HostProjections(), LocalDate::now);

    @Transactional
    public void processOrderCreated(Long orderId, Long userId, Long restaurantId,
                                   String restaurantName, BigDecimal totalPrice, String paymentMethod, String rawPayload) {
        if (process("ORDER_CREATED",orderId,userId,restaurantId,restaurantName,totalPrice,"PENDING",paymentMethod,rawPayload) && !isPostgres())
            log.info("📊 Processed ORDER_CREATED: orderId={}, restaurantId={}", orderId, restaurantId);
    }
    @Transactional
    public void processOrderDelivered(Long orderId, Long restaurantId, String restaurantName,
                                     BigDecimal totalPrice, String rawPayload) {
        if (process("ORDER_DELIVERED",orderId,null,restaurantId,restaurantName,totalPrice,"DELIVERED",null,rawPayload) && !isPostgres())
            log.info("📊 Processed ORDER_DELIVERED: orderId={}, revenue={}", orderId, totalPrice);
    }
    @Transactional
    public void processOrderCancelled(Long orderId, Long restaurantId, String rawPayload) {
        if (process("ORDER_CANCELLED",orderId,null,restaurantId,null,null,"CANCELLED",null,rawPayload) && !isPostgres())
            log.info("📊 Processed ORDER_CANCELLED: orderId={}", orderId);
    }
    @Transactional
    public void processPaymentCompleted(Long orderId, Long userId, Double amount, String paymentMethod, String rawPayload) {
        String key=resolveDeduplicationKey("PAYMENT_COMPLETED",orderId,rawPayload);
        BigDecimal safeAmount=amount!=null?BigDecimal.valueOf(amount):BigDecimal.ZERO;
        if (ingest(key,"PAYMENT_COMPLETED",orderId,userId,null,null,safeAmount,null,paymentMethod,rawPayload) && !isPostgres())
            log.info("📊 Processed PAYMENT_COMPLETED: orderId={}, amount={}", orderId, amount);
    }
    @Transactional
    public void processPaymentFailed(Long orderId, String rawPayload) {
        if (process("PAYMENT_FAILED",orderId,null,null,null,null,null,null,rawPayload) && !isPostgres())
            log.info("📊 Processed PAYMENT_FAILED: orderId={}", orderId);
    }
    private boolean process(String type, Long orderId, Long userId, Long restaurantId, String restaurantName,
                         BigDecimal amount, String status, String paymentMethod, String rawPayload) {
        String key=resolveDeduplicationKey(type,orderId,rawPayload);
        return ingest(key,type,orderId,userId,restaurantId,restaurantName,amount,status,paymentMethod,rawPayload);
    }
    private boolean ingest(String key, String type, Long orderId, Long userId, Long restaurantId, String restaurantName,
                        BigDecimal amount, String status, String paymentMethod, String rawPayload) {
        if(rawPayload==null || rawPayload.isBlank()) throw new IllegalArgumentException("Analytics raw payload is required");
        String hash=fingerprint(rawPayload);
        Long version=aggregateVersion(rawPayload);
        return ingestion.ingest(new IngestionUseCase.Command(key,new ReceiptIdentity(type,orderId,userId,restaurantId,
                restaurantName,amount,status,paymentMethod,version,rawPayload,hash)));
    }
    /** Persistence adapter: native atomic SQL or equivalent domain read/modify/save. */
    private final class HostProjections implements IngestionPorts.Projections {
        public void order(String type, LocalDate date, Long restaurantId, BigDecimal amount) {
            if(isPostgres()) {
                switch(type) {
                    case "ORDER_CREATED" -> orderStatsRepo.incrementCreatedPostgres(date,restaurantId);
                    case "ORDER_DELIVERED" -> orderStatsRepo.incrementDeliveredPostgres(date,restaurantId,
                            amount!=null?amount:BigDecimal.ZERO);
                    case "ORDER_CANCELLED" -> orderStatsRepo.incrementCancelledPostgres(date,restaurantId);
                    default -> throw new IllegalArgumentException(type);
                }
                return;
            }
            var stats=getOrCreateOrderStats(date,restaurantId);
            var before=new OrderProjection(stats.getTotalOrders(),stats.getDeliveredOrders(),stats.getCancelledOrders(),
                    stats.getPendingOrders(),stats.getTotalRevenue(),stats.getAvgOrderValue());
            var after=switch(type) {
                case "ORDER_CREATED" -> before.created();
                case "ORDER_DELIVERED" -> before.delivered(amount);
                case "ORDER_CANCELLED" -> before.cancel();
                default -> throw new IllegalArgumentException(type);
            };
            stats.setTotalOrders(after.total()); stats.setDeliveredOrders(after.delivered());
            stats.setCancelledOrders(after.cancelled()); stats.setPendingOrders(after.pending());
            stats.setTotalRevenue(after.revenue()); stats.setAvgOrderValue(after.average());
            orderStatsRepo.save(stats);
        }
        public void payment(String type, LocalDate date, BigDecimal amount) {
            boolean completed=type.equals("PAYMENT_COMPLETED");
            if(isPostgres()) {
                if(completed) revenueStatsRepo.incrementPaymentCompletedPostgres(date,amount);
                else revenueStatsRepo.incrementPaymentFailedPostgres(date);
                return;
            }
            var stats=getOrCreatePlatformRevenueStats(date);
            var before=new PaymentProjection(stats.getSuccessfulPayments(),stats.getFailedPayments(),stats.getTotalPaymentAmount());
            var after=completed?before.completed(amount):before.failure();
            stats.setSuccessfulPayments(after.successful());
            stats.setFailedPayments(after.failed());
            stats.setTotalPaymentAmount(after.amount());
            revenueStatsRepo.save(stats);
        }
        public boolean itemsEnabled(Long restaurantId) { return itemSalesRepo!=null && restaurantId!=null; }
        public void item(LocalDate statDate, Long restaurantId, Item line, boolean cancelled) {
            long menuItemId=line.menuItemId(); String menuItemName=line.menuItemName();
            var delta=line.delta(cancelled);
            long orderedQuantity=delta.orderedQuantity(), cancelledQuantity=delta.cancelledQuantity();
            BigDecimal orderedRevenue=delta.orderedRevenue(), cancelledRevenue=delta.cancelledRevenue();
            if (isPostgres()) {
                itemSalesRepo.incrementPostgres(statDate, restaurantId, menuItemId, menuItemName,
                        orderedQuantity, cancelledQuantity, orderedRevenue, cancelledRevenue,
                        LocalDateTime.now());
            } else {
                DailyItemSales aggregate = itemSalesRepo
                        .findByStatDateAndRestaurantIdAndMenuItemId(statDate, restaurantId, menuItemId)
                        .orElseGet(() -> DailyItemSales.builder()
                                .statDate(statDate).restaurantId(restaurantId).menuItemId(menuItemId)
                                .menuItemName(menuItemName).orderedQuantity(0).cancelledQuantity(0)
                                .orderedRevenue(BigDecimal.ZERO).cancelledRevenue(BigDecimal.ZERO)
                                .updatedAt(LocalDateTime.now()).build());
                aggregate.setMenuItemName(menuItemName);
                aggregate.setOrderedQuantity(aggregate.getOrderedQuantity() + orderedQuantity);
                aggregate.setCancelledQuantity(aggregate.getCancelledQuantity() + cancelledQuantity);
                aggregate.setOrderedRevenue(aggregate.getOrderedRevenue().add(orderedRevenue));
                aggregate.setCancelledRevenue(aggregate.getCancelledRevenue().add(cancelledRevenue));
                aggregate.setUpdatedAt(LocalDateTime.now());
                itemSalesRepo.save(aggregate);
            }
        }
    }

    private JsonNode readJson(String rawPayload) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(rawPayload);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("analytics raw payload is not valid JSON", invalid);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("analytics raw payload must be a JSON object");
        }
        return root;
    }

    private LocalDate eventDate(String rawPayload, LocalDate fallback) {
        JsonNode root = readJson(rawPayload);
        String selected=SnapshotDecisions.firstTimestamp(text(root,"occurredAt"),text(root,"eventTimestamp"),text(root,"createdAt"));
        if(selected==null) return fallback;
        try { return LocalDateTime.parse(selected).toLocalDate(); }
        catch(RuntimeException invalid) { throw new IllegalArgumentException("analytics event timestamp is invalid",invalid); }
    }
    private String text(JsonNode root, String field) {
        JsonNode value=root.get(field);
        return value==null || value.isNull()?null:value.asText();
    }

    private boolean claim(String key, ReceiptIdentity identity) {
        String type=identity.eventType(), restaurantName=identity.restaurantName(), orderStatus=identity.orderStatus(),
                paymentMethod=identity.paymentMethod(), rawPayload=identity.rawPayload(), fingerprint=identity.payloadFingerprint();
        Long orderId=identity.orderId(),userId=identity.userId(),restaurantId=identity.restaurantId(),aggregateVersion=identity.aggregateVersion();
        BigDecimal amount=identity.amount();
        AnalyticsEvent incoming = AnalyticsEvent.builder()
                .deduplicationKey(key).eventType(type).eventTime(LocalDateTime.now())
                .orderId(orderId).userId(userId).restaurantId(restaurantId)
                .restaurantName(restaurantName).amount(amount).orderStatus(orderStatus)
                .paymentMethod(paymentMethod).rawPayload(rawPayload)
                .payloadFingerprint(fingerprint).aggregateVersion(aggregateVersion).build();
        AnalyticsEvent existing = eventRepo.findByDeduplicationKey(key).orElse(null);
        if (existing == null) {
            if (isPostgres()) {
                int inserted = eventRepo.insertIfAbsentPostgres(key, type, orderId, userId,
                        restaurantId, restaurantName, amount, orderStatus, paymentMethod,
                        rawPayload, fingerprint, aggregateVersion);
                if (inserted == 1) return true;
                existing = eventRepo.findByDeduplicationKey(key).orElseThrow(() ->
                        new IllegalStateException("analytics receipt conflict resolved without a committed row"));
            } else {
                eventRepo.saveAndFlush(incoming);
                return true;
            }
        }
        AnalyticsReplayPolicy.requireExactReplay(existing, incoming);
        log.info("Skipping exact analytics replay {}", key);
        return false;
    }

    private boolean isPostgres() {
        return dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:postgresql:");
    }

    private String fingerprint(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Long aggregateVersion(String rawPayload) {
        JsonNode value = readJson(rawPayload).get("aggregateVersion");
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("analytics aggregateVersion must be positive");
        }
        return SnapshotDecisions.positive(value.asLong(), "analytics aggregateVersion must be positive");
    }

    static String resolveDeduplicationKey(String eventType, Long orderId, String rawPayload) {
        if (rawPayload != null && !rawPayload.isBlank()) {
            try {
                JsonNode eventId = OBJECT_MAPPER.readTree(rawPayload).path("eventId");
                if (eventId.isTextual() && !eventId.asText().isBlank()) {
                    return ReceiptKey.resolve(eventType, orderId, eventId.asText());
                }
            } catch (Exception ignored) {
                // Listener owns payload validation; legacy producers may not carry eventId.
            }
        }
        return ReceiptKey.resolve(eventType, orderId, null);
    }

    private DailyOrderStats getOrCreateOrderStats(LocalDate date, Long restaurantId) {
        if (restaurantId == null) {
            return orderStatsRepo.findByStatDateAndRestaurantIdIsNull(date)
                    .orElseGet(() -> DailyOrderStats.builder()
                            .statDate(date)
                            .restaurantId(null)
                            .totalOrders(0)
                            .deliveredOrders(0)
                            .cancelledOrders(0)
                            .pendingOrders(0)
                            .totalRevenue(BigDecimal.ZERO)
                            .totalShippingFee(BigDecimal.ZERO)
                            .totalDiscount(BigDecimal.ZERO)
                            .avgOrderValue(BigDecimal.ZERO)
                            .newCustomers(0)
                            .build());
        }
        return orderStatsRepo.findByStatDateAndRestaurantId(date, restaurantId)
                .orElseGet(() -> DailyOrderStats.builder()
                        .statDate(date)
                        .restaurantId(restaurantId)
                        .totalOrders(0)
                        .deliveredOrders(0)
                        .cancelledOrders(0)
                        .pendingOrders(0)
                        .totalRevenue(BigDecimal.ZERO)
                        .totalShippingFee(BigDecimal.ZERO)
                        .totalDiscount(BigDecimal.ZERO)
                        .avgOrderValue(BigDecimal.ZERO)
                        .newCustomers(0)
                        .build());
    }

    private DailyRevenueStats getOrCreatePlatformRevenueStats(LocalDate date) {
        return revenueStatsRepo.findByStatDateAndRestaurantIdIsNull(date)
                .orElseGet(() -> DailyRevenueStats.builder()
                        .statDate(date)
                        .restaurantId(null)
                        .totalPaymentAmount(BigDecimal.ZERO)
                        .successfulPayments(0)
                        .failedPayments(0)
                        .totalWithdrawals(BigDecimal.ZERO)
                        .platformFee(BigDecimal.ZERO)
                        .build());
    }

}
