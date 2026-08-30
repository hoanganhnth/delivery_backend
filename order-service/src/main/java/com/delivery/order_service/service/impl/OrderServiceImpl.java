package com.delivery.order_service.service.impl;

import com.delivery.order_service.common.constants.RoleConstants;
import com.delivery.order_service.dto.internal.ValidatedOrderData;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.response.OrderResponse;
import com.delivery.order_service.dto.event.ShipperNotFoundEvent;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.entity.OrderItem;
import com.delivery.order_service.entity.OrderStatus;
import com.delivery.order_service.exception.AccessDeniedException;
import com.delivery.order_service.exception.OrderApiException;
import com.delivery.order_service.exception.ResourceNotFoundException;
import com.delivery.order_service.mapper.OrderMapper;
import com.delivery.order_service.repository.OrderItemRepository;
import com.delivery.order_service.repository.OrderRepository;
import com.delivery.order_service.service.OrderEventPublisher;
import com.delivery.order_service.service.OrderService;
import com.delivery.order_service.service.OrderValidationService;
import com.delivery.order_service.service.ShippingFeeCalculationService;
import com.delivery.order_service.service.CheckoutReservationClient;
import com.delivery.order_service.service.CheckoutQuoteService;
import com.delivery.order_service.service.CheckoutFingerprintService;
import com.delivery.order_service.service.OrderCreateIdempotencyService;
import com.delivery.order_service.config.OrderCreateAdmission;
import com.delivery.order_service.entity.OrderCreateIdempotencyReceipt;
import com.delivery.identity.contracts.SimulationContext;
import com.delivery.order_service.metrics.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderValidationService orderValidationService;
    private final ShippingFeeCalculationService shippingFeeCalculationService;
    private final BusinessMetrics businessMetrics;
    private final CheckoutReservationClient reservationClient;
    private final OrderCreateAdmission createAdmission;

    @Autowired(required = false)
    private com.delivery.order_service.service.InventoryReservationClient inventoryReservationClient;

    @Value("${app.order.inventory-reservation-enabled:false}")
    private boolean inventoryReservationEnabled;

    @Autowired(required = false)
    private CheckoutQuoteService checkoutQuoteService;

    @Autowired(required = false)
    private OrderCreateIdempotencyService idempotencyService;

    @Autowired(required = false)
    private CheckoutFingerprintService checkoutFingerprintService;

    /**
     * Explicit transaction boundary for create-order.  The old implementation
     * opened a write transaction before calling remote dependencies.  Keeping
     * this optional preserves source-compatible focused unit tests; production
     * Spring wiring always supplies the template from the service DB manager.
     */
    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    @Value("${app.identity.principal-ownership.enforced:false}")
    private boolean principalOwnershipEnforced;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           OrderMapper orderMapper,
                           OrderEventPublisher orderEventPublisher,
                           OrderValidationService orderValidationService,
                           ShippingFeeCalculationService shippingFeeCalculationService,
                           BusinessMetrics businessMetrics,
                           CheckoutReservationClient reservationClient,
                           OrderCreateAdmission createAdmission) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderMapper = orderMapper;
        this.orderEventPublisher = orderEventPublisher;
        this.orderValidationService = orderValidationService;
        this.shippingFeeCalculationService = shippingFeeCalculationService;
        this.businessMetrics = businessMetrics;
        this.reservationClient = reservationClient;
        this.createAdmission = createAdmission;
    }

    /** Source-compatible constructor for focused legacy tests/callers. */
    public OrderServiceImpl(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           OrderMapper orderMapper,
                           OrderEventPublisher orderEventPublisher,
                           OrderValidationService orderValidationService,
                           ShippingFeeCalculationService shippingFeeCalculationService,
                           BusinessMetrics businessMetrics,
                           CheckoutReservationClient reservationClient) {
        this(orderRepository, orderItemRepository, orderMapper, orderEventPublisher,
                orderValidationService, shippingFeeCalculationService, businessMetrics,
                reservationClient, null);
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request, Long userId, String role) {
        return createOrder(request, userId, userId, role);
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request, Long principalId, Long userId, String role) {
        return createOrder(request, null, principalId, userId, role);
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request, UUID idempotencyKey,
                                     Long principalId, Long userId, String role) {
        return createOrder(request, idempotencyKey, principalId, userId, role, SimulationContext.real());
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request, UUID idempotencyKey,
                                     Long principalId, Long userId, String role,
                                     SimulationContext simulationContext) {
        // Reject unauthorized traffic before consuming an admission permit.
        if (!RoleConstants.USER.equals(role)) {
            throw new AccessDeniedException("Chỉ khách hàng được tạo đơn hàng");
        }
        if (createAdmission == null) {
            return createOrderInternal(request, idempotencyKey, principalId, userId, role,
                    SimulationContext.orReal(simulationContext));
        }
        return createAdmission.execute(() -> createOrderInternal(
                request, idempotencyKey, principalId, userId, role,
                SimulationContext.orReal(simulationContext)));
    }

    private OrderResponse createOrderInternal(CreateOrderRequest request, UUID idempotencyKey,
                                              Long principalId, Long userId, String role,
                                              SimulationContext simulationContext) {
        if (!RoleConstants.USER.equals(role)) {
            throw new AccessDeniedException("Chỉ khách hàng được tạo đơn hàng");
        }
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đơn hàng không được để trống");
        }

        String requestFingerprint = null;
        UUID processingToken = null;
        OrderCreateIdempotencyReceipt processingReceipt = null;
        if (idempotencyKey != null) {
            if (idempotencyService == null || checkoutFingerprintService == null) {
                throw new IllegalStateException("Create-order idempotency is unavailable");
            }

            requestFingerprint = checkoutFingerprintService.createCommand(request);
            processingToken = UUID.randomUUID();
            processingReceipt = idempotencyService.acquire(
                    principalId, idempotencyKey, requestFingerprint, processingToken);
            if (processingReceipt.getOrderId() != null) {
                return loadOrderResponse(processingReceipt.getOrderId(), principalId, userId, role);
            }
        }

        // All remote/read-heavy preflight work happens before the write
        // transaction.  The final transaction still re-locks/consumes the quote
        // and claims idempotency, so a concurrent request cannot create a second
        // order.
        ValidatedOrderData validated;
        try {
            validated = prepareCreateOrder(request, principalId, userId);
        } catch (RuntimeException failure) {
            releaseIdempotencyLease(processingReceipt, processingToken, failure);
            throw failure;
        }

        String finalFingerprint = requestFingerprint;
        UUID finalProcessingToken = processingToken;
        try {
            return executeWriteTransaction(() -> persistCreateOrder(
                    request, idempotencyKey, finalFingerprint, finalProcessingToken,
                    principalId, userId, role, validated, simulationContext));
        } catch (RuntimeException failure) {
            releaseIdempotencyLease(processingReceipt, processingToken, failure);
            throw failure;
        }
    }

    private ValidatedOrderData prepareCreateOrder(CreateOrderRequest request, Long principalId, Long userId) {
        if (request.getQuoteId() != null) {
            if (checkoutQuoteService == null) {
                throw new IllegalStateException("Checkout quote service is unavailable");
            }
            checkoutQuoteService.validateAndReprice(request, principalId, userId);
        }
        // ✅ Validate request + lấy canonical restaurant data từ server (1 lần duy nhất gọi restaurant-service)
        ValidatedOrderData validated = orderValidationService.validateCreateOrderRequest(request, principalId, userId);

        if (validated == null || validated.creatorId() == null) {
            throw new ResourceNotFoundException(
                    "Không thể lấy thông tin nhà hàng. Restaurant ID: " + request.getRestaurantId()
            );
        }

        return validated;
    }

    private OrderResponse persistCreateOrder(CreateOrderRequest request, UUID idempotencyKey,
                                              String requestFingerprint, UUID processingToken,
                                              Long principalId, Long userId,
                                              String role, ValidatedOrderData validated,
                                              SimulationContext simulationContext) {
        OrderCreateIdempotencyReceipt idempotencyReceipt = null;
        if (idempotencyKey != null) {
            if (idempotencyService == null || checkoutFingerprintService == null) {
                throw new IllegalStateException("Create-order idempotency is unavailable");
            }
            idempotencyReceipt = idempotencyService.claim(principalId, idempotencyKey, requestFingerprint,
                    processingToken);
            if (idempotencyReceipt.getOrderId() != null) {
                return loadOrderResponse(idempotencyReceipt.getOrderId(), principalId, userId, role);
            }
        }

        log.info("✅ Restaurant validated from server. creatorId={}, name={}",
                validated.creatorId(), validated.restaurantName());

        // ✅ Mapper chỉ copy: restaurantId, deliveryAddress, deliveryLat/Lng,
        //   customerName, customerPhone, paymentMethod, notes.
        //   Các trường nhà hàng sẽ được set rõ ràng từ ValidatedOrderData bên dưới.
        Order order = orderMapper.createOrderRequestToOrder(request);
        order.setSimulationContext(simulationContext);
        order.setUserId(userId);
        order.setUserPrincipalId(principalId);

        // ✅ Set canonical restaurant data từ server — không dùng bất cứ dữ liệu nào từ client
        order.setCreatorId(validated.creatorId());
        order.setCreatorPrincipalId(validated.creatorPrincipalId());
        order.setRestaurantName(validated.restaurantName());
        order.setRestaurantAddress(validated.restaurantAddress());
        order.setRestaurantPhone(validated.restaurantPhone());
        order.setPickupLat(validated.pickupLat());
        order.setPickupLng(validated.pickupLng());

        Map<Long, ValidatedOrderData.ValidatedItemData> canonicalItems = validated.items().stream()
                .collect(Collectors.toMap(
                        ValidatedOrderData.ValidatedItemData::menuItemId,
                        Function.identity()));
        if (canonicalItems.size() != request.getItems().size()) {
            throw new IllegalStateException("Restaurant service không trả đủ dữ liệu canonical của món ăn");
        }

        order.setSubtotalPrice(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalPrice(BigDecimal.ZERO);
        order.setItemDiscount(BigDecimal.ZERO);
        order.setShippingDiscount(BigDecimal.ZERO);
        order.setCustomerShippingFee(BigDecimal.ZERO);
        order.setGrossShippingFee(BigDecimal.ZERO);
        order.setPlatformSubsidy(BigDecimal.ZERO);
        order.setShopDiscount(BigDecimal.ZERO);
        Order savedOrder = orderRepository.saveAndFlush(order);

        UUID voucherReservationId = null;
        UUID promotionReservationId = null;
        UUID flashReservationId = null;
        UUID inventoryReservationId = null;
        try {
            if (inventoryReservationEnabled) {
                inventoryReservationId = UUID.randomUUID();
                reserveInventory(inventoryReservationId, savedOrder.getId(), userId, principalId,
                        request.getRestaurantId(), request.getItems());
                savedOrder.setInventoryReservationId(inventoryReservationId);
            }

            boolean hasFlash = request.getItems().stream().anyMatch(item -> item.getFlashSaleItemId() != null);
            CheckoutReservationClient.FlashQuote flashQuote = null;
            if (hasFlash) {
                flashReservationId = UUID.randomUUID();
                flashQuote = reserveFlash(flashReservationId, savedOrder.getId(), userId, principalId,
                        request.getRestaurantId(), request.getItems());
                savedOrder.setFlashSaleReservationId(flashReservationId);
            }

            CheckoutReservationClient.FlashQuote canonicalFlashQuote = flashQuote;
            BigDecimal subtotal = request.getItems().stream().map(item -> {
                BigDecimal unitPrice = requireCanonicalItem(canonicalItems, item.getMenuItemId()).price();
                if (item.getFlashSaleItemId() != null) {
                    CheckoutReservationClient.FlashLine line = canonicalFlashQuote.byFlashSaleItemId()
                            .get(item.getFlashSaleItemId());
                    if (line == null || !item.getMenuItemId().equals(line.menuItemId())
                            || !item.getQuantity().equals(line.quantity()))
                        throw new IllegalStateException("Flash-sale canonical item mismatch");
                    unitPrice = line.unitPrice();
                }
                return unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            }).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal shippingFee = shippingFeeCalculationService.calculateShippingFee(
                    validated.pickupLat(), validated.pickupLng(), request.getDeliveryLat(),
                    request.getDeliveryLng(), subtotal);
            BigDecimal discount = BigDecimal.ZERO;
            CheckoutReservationClient.PromotionQuote promotionQuote = null;
            CheckoutReservationClient.VoucherQuote legacyQuote = null;
            List<Long> selectedVoucherIds = normalizedVoucherIds(request);
            if ("AUTO".equalsIgnoreCase(request.getSelectionMode()) && selectedVoucherIds.isEmpty()) {
                CheckoutReservationClient.PromotionQuote autoQuote = reservationClient.quoteVouchers(
                        userId, principalId, request.getRestaurantId(), subtotal, shippingFee,
                        List.of(), "AUTO");
                selectedVoucherIds = autoQuote.selectedVoucherIds();
            }
            if ("MANUAL".equalsIgnoreCase(request.getSelectionMode()) && selectedVoucherIds.isEmpty()) {
                throw new IllegalArgumentException("Manual voucher mode requires selected voucher IDs");
            }
            if (!selectedVoucherIds.isEmpty()
                    && (selectedVoucherIds.size() > 1 || request.getSelectionMode() != null)) {
                promotionReservationId = UUID.randomUUID();
                promotionQuote = reserveVouchers(promotionReservationId, savedOrder.getId(), userId, principalId,
                        request.getRestaurantId(), subtotal, shippingFee, selectedVoucherIds);
                savedOrder.setPromotionReservationId(promotionReservationId);
                discount = promotionQuote.totalDiscount();
                savedOrder.setItemDiscount(promotionQuote.itemDiscount());
                savedOrder.setShippingDiscount(promotionQuote.shippingDiscount());
                savedOrder.setCustomerShippingFee(promotionQuote.customerShippingFee());
                savedOrder.setGrossShippingFee(shippingFee);
                savedOrder.setPlatformSubsidy(promotionQuote.platformSubsidy());
                savedOrder.setShopDiscount(promotionQuote.shopDiscount());
                savedOrder.setPromotionBreakdown(promotionQuote.breakdownJson());
            } else if (selectedVoucherIds.size() == 1) {
                // Legacy single-voucher clients keep their old reservation rail
                // until they send selectionMode/selectedVoucherIds explicitly.
                voucherReservationId = UUID.randomUUID();
                legacyQuote = reserveVoucher(voucherReservationId, savedOrder.getId(), userId, principalId,
                        selectedVoucherIds.get(0), request.getRestaurantId(), subtotal, shippingFee);
                discount = legacyQuote.discountAmount();
                savedOrder.setVoucherReservationId(voucherReservationId);
                savedOrder.setItemDiscount(legacyQuote.itemDiscount());
                savedOrder.setShippingDiscount(legacyQuote.shippingDiscount());
                savedOrder.setCustomerShippingFee(legacyQuote.customerShippingFee() == null
                        ? shippingFee.subtract(legacyQuote.shippingDiscount()).max(BigDecimal.ZERO)
                        : legacyQuote.customerShippingFee());
                savedOrder.setGrossShippingFee(shippingFee);
                savedOrder.setPlatformSubsidy(legacyQuote.platformSubsidy());
                savedOrder.setShopDiscount(legacyQuote.shopDiscount());
                savedOrder.setPromotionBreakdown(legacyQuote.breakdownJson());
            }
            if (discount.signum() < 0 || discount.compareTo(subtotal.add(shippingFee)) > 0)
                throw new IllegalStateException("Reservation service returned an invalid discount");

            savedOrder.setSubtotalPrice(subtotal);
            savedOrder.setShippingFee(shippingFee);
            savedOrder.setDiscountAmount(discount);
            if (promotionQuote == null && legacyQuote == null) {
                // No voucher: customer pays the canonical gross shipping fee.
                savedOrder.setItemDiscount(BigDecimal.ZERO);
                savedOrder.setShippingDiscount(BigDecimal.ZERO);
                savedOrder.setCustomerShippingFee(shippingFee);
                savedOrder.setGrossShippingFee(shippingFee);
                savedOrder.setPlatformSubsidy(BigDecimal.ZERO);
                savedOrder.setShopDiscount(BigDecimal.ZERO);
            }
            savedOrder.setTotalPrice(subtotal.subtract(savedOrder.getItemDiscount()).add(
                    savedOrder.getCustomerShippingFee()));
            if (savedOrder.getTotalPrice().compareTo(shippingFee) <= 0) {
                throw new IllegalStateException("Voucher must leave a positive payable food amount");
            }

            List<OrderItem> orderItems = request.getItems().stream().map(itemRequest -> {
                ValidatedOrderData.ValidatedItemData canonical = requireCanonicalItem(canonicalItems,
                        itemRequest.getMenuItemId());
                OrderItem item = orderMapper.orderItemRequestToOrderItem(itemRequest);
                item.setMenuItemName(canonical.menuItemName());
                item.setPrice(itemRequest.getFlashSaleItemId() == null ? canonical.price()
                        : canonicalFlashQuote.byFlashSaleItemId().get(itemRequest.getFlashSaleItemId()).unitPrice());
                item.setOrder(savedOrder);
                return item;
            }).collect(Collectors.toCollection(ArrayList::new));
            orderItemRepository.saveAll(orderItems);
            savedOrder.setItems(orderItems);
            orderRepository.save(savedOrder);
            if (inventoryReservationId != null) {
                commitInventory(inventoryReservationId, savedOrder.getId());
            }
            if (request.getQuoteId() != null) {
                checkoutQuoteService.consume(request.getQuoteId(), principalId, savedOrder.getId());
            }
            if (idempotencyReceipt != null) {
                idempotencyService.complete(idempotencyReceipt, savedOrder.getId());
            }
            orderEventPublisher.publishOrderCreatedEvent(savedOrder);
            businessMetrics.record("order_created");
            log.info("Order created id={}, subtotal={}, discount={}, shipping={}, total={}", savedOrder.getId(),
                    subtotal, discount, shippingFee, savedOrder.getTotalPrice());
            return orderMapper.orderToOrderResponse(savedOrder);
        } catch (RuntimeException failure) {
            compensateReservation(voucherReservationId, promotionReservationId, flashReservationId,
                    inventoryReservationId, savedOrder.getId(), principalId, failure);
            throw failure;
        }
    }

    private OrderResponse loadOrderResponse(Long orderId, Long principalId, Long userId, String role) {
        if (transactionTemplate == null) {
            Order order = findOrderById(orderId);
            validateViewPermission(order, principalId, userId, role);
            return orderMapper.orderToOrderResponse(order);
        }
        return requireTransactionResult(transactionTemplate.execute(status -> {
            Order order = findOrderById(orderId);
            validateViewPermission(order, principalId, userId, role);
            return orderMapper.orderToOrderResponse(order);
        }));
    }

    private OrderResponse executeWriteTransaction(Supplier<OrderResponse> operation) {
        if (transactionTemplate == null) {
            return operation.get();
        }
        return requireTransactionResult(transactionTemplate.execute(status -> operation.get()));
    }

    private OrderResponse requireTransactionResult(OrderResponse response) {
        if (response == null) {
            throw new IllegalStateException("Order transaction returned no response");
        }
        return response;
    }

    private void compensateReservation(UUID voucherId, UUID promotionId, UUID flashId,
                                       UUID inventoryId, Long orderId,
                                       Long userPrincipalId, RuntimeException failure) {
        if (voucherId != null) try { reservationClient.releaseVoucher(voucherId, orderId); }
        catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
        if (promotionId != null) try { reservationClient.releaseVouchers(promotionId, orderId, userPrincipalId); }
        catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
        if (flashId != null) try { reservationClient.releaseFlash(flashId, orderId); }
        catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
        if (inventoryId != null) try {
            requireInventoryClient().release(inventoryId, orderId);
        } catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
    }

    private void reserveInventory(UUID reservationId, Long orderId, Long userId, Long principalId,
                                  Long restaurantId, List<CreateOrderRequest.OrderItemRequest> items) {
        requireInventoryClient().reserve(reservationId, orderId, userId, principalId, restaurantId, items);
    }

    private void commitInventory(UUID reservationId, Long orderId) {
        requireInventoryClient().commit(reservationId, orderId);
    }

    private com.delivery.order_service.service.InventoryReservationClient requireInventoryClient() {
        if (inventoryReservationClient == null) {
            throw new com.delivery.order_service.exception.OrderDependencyUnavailableException(
                    "restaurant-service", "Inventory reservation client is unavailable", null, 30);
        }
        return inventoryReservationClient;
    }

    private void releaseIdempotencyLease(OrderCreateIdempotencyReceipt receipt, UUID processingToken,
                                         RuntimeException originalFailure) {
        if (receipt == null || processingToken == null || receipt.getOrderId() != null) return;
        try {
            idempotencyService.release(receipt.getId(), processingToken);
        } catch (RuntimeException releaseFailure) {
            originalFailure.addSuppressed(releaseFailure);
        }
    }

    /**
     * Preserve the legacy reservation payload during the identity migration
     * compatibility window. New principal/legacy pairs carry the stable
     * principal ID to downstream reservation services.
     */
    private CheckoutReservationClient.VoucherQuote reserveVoucher(
            UUID reservationId, Long orderId, Long userId, Long principalId,
            Long voucherId, Long restaurantId, BigDecimal subtotal, BigDecimal shippingFee) {
        if (principalId == null || Objects.equals(principalId, userId)) {
            return reservationClient.reserveVoucher(reservationId, orderId, userId, voucherId,
                    restaurantId, subtotal, shippingFee);
        }
        return reservationClient.reserveVoucher(reservationId, orderId, userId, principalId, voucherId,
                restaurantId, subtotal, shippingFee);
    }

    private CheckoutReservationClient.FlashQuote reserveFlash(
            UUID reservationId, Long orderId, Long userId, Long principalId,
            Long restaurantId, List<CreateOrderRequest.OrderItemRequest> requestItems) {
        if (principalId == null || Objects.equals(principalId, userId)) {
            return reservationClient.reserveFlash(reservationId, orderId, userId, restaurantId, requestItems);
        }
        return reservationClient.reserveFlash(reservationId, orderId, userId, principalId, restaurantId, requestItems);
    }

    private CheckoutReservationClient.PromotionQuote reserveVouchers(
            UUID reservationId, Long orderId, Long userId, Long principalId, Long restaurantId,
            BigDecimal subtotal, BigDecimal shippingFee, List<Long> voucherIds) {
        return reservationClient.reserveVouchers(reservationId, orderId, userId, principalId, restaurantId,
                subtotal, shippingFee, voucherIds);
    }

    private List<Long> normalizedVoucherIds(CreateOrderRequest request) {
        List<Long> ids = request.getVoucherIds() == null
                ? new ArrayList<>() : new ArrayList<>(request.getVoucherIds());
        if (ids.size() > 3 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("At most three distinct voucher IDs are supported");
        }
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, Long userId, String role) {
        return getOrderById(id, userId, userId, role);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, Long principalId, Long legacyUserId, String role) {
        Order order = findOrderById(id);

        // Kiểm tra quyền xem
        validateViewPermission(order, principalId, legacyUserId, role);

        return orderMapper.orderToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUser(Long userId, Long requesterId, String role, Pageable pageable) {
        if (!RoleConstants.ADMIN.equals(role) && !userId.equals(requesterId)) {
            throw new AccessDeniedException("Bạn chỉ có thể xem đơn hàng của chính mình");
        }
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orders.map(orderMapper::orderToOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByPrincipal(Long principalId, Long legacyUserId, String role, Pageable pageable) {
        if (principalId == null || legacyUserId == null) {
            throw new AccessDeniedException("Missing authenticated identity");
        }
        Page<Order> orders = principalOwnershipEnforced
                ? orderRepository.findByUserPrincipalIdOrderByCreatedAtDesc(principalId, pageable)
                : orderRepository.findByPrincipalOrUnmigratedLegacyUserOrderByCreatedAtDesc(
                        principalId, legacyUserId, pageable);
        orders.forEach(order -> {
            if (order.getUserPrincipalId() == null) businessMetrics.identityLegacyFallback("customer_list");
        });
        return orders.map(orderMapper::orderToOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByRestaurantOwner(Long principalId, Long legacyOwnerId, String role,
            Pageable pageable) {
        // Chỉ admin hoặc chính restaurant owner mới được xem
        if (!RoleConstants.ADMIN.equals(role)) {
            if (!RoleConstants.RESTAURANT_OWNER.equals(role)) {
                throw new AccessDeniedException("Bạn không có quyền xem đơn hàng của chủ nhà hàng");
            }
        }
        if (principalId == null || legacyOwnerId == null) {
            throw new AccessDeniedException("Missing authenticated identity");
        }

        // No hot-path lookup into Restaurant/Auth. New rows use the stable
        // principal; legacy rows remain readable only while their principal is absent.
        log.info("📋 Getting orders for restaurant owner principal={}", principalId);

        Page<Order> orders = principalOwnershipEnforced
                ? orderRepository.findByCreatorPrincipalIdOrderByCreatedAtDesc(principalId, pageable)
                : orderRepository.findByRestaurantOwnerPrincipalOrUnmigratedLegacyOrderByCreatedAtDesc(
                        principalId, legacyOwnerId, pageable);
        orders.forEach(order -> {
            if (order.getCreatorPrincipalId() == null) businessMetrics.identityLegacyFallback("restaurant_owner_list");
        });
        log.info("✅ Found {} orders for restaurant owner principal {}", orders.getTotalElements(), principalId);

        return orders.map(orderMapper::orderToOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByStatus(String status, Long userId, String role, Pageable pageable) {
        if (!RoleConstants.ADMIN.equals(role)) {
            throw new AccessDeniedException("Chỉ admin được lọc toàn hệ thống theo trạng thái");
        }
        Page<Order> orders = orderRepository.findByStatusOrderByCreatedAtDesc(
                OrderStatus.fromExternal(status), pageable);
        return orders.map(orderMapper::orderToOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Long userId, String role, Pageable pageable) {
        // Chỉ admin mới được xem tất cả đơn hàng
        if (!RoleConstants.ADMIN.equals(role)) {
            throw new AccessDeniedException("Bạn không có quyền xem tất cả đơn hàng");
        }

        Page<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc(pageable);
        return orders.map(orderMapper::orderToOrderResponse);
    }

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));
    }

    private Order findOrderByIdForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));
    }

    private ValidatedOrderData.ValidatedItemData requireCanonicalItem(
            Map<Long, ValidatedOrderData.ValidatedItemData> canonicalItems,
            Long menuItemId) {
        ValidatedOrderData.ValidatedItemData item = canonicalItems.get(menuItemId);
        if (item == null) {
            throw new IllegalStateException("Thiếu dữ liệu canonical cho menu item " + menuItemId);
        }
        return item;
    }

    private void validateViewPermission(Order order, Long principalId, Long legacyUserId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể xem tất cả
        }

        if (RoleConstants.USER.equals(role)) {
            if (order.getUserPrincipalId() != null && principalId != null
                    && order.getUserPrincipalId().equals(principalId)) return;
            if (!principalOwnershipEnforced && order.getUserPrincipalId() == null
                    && order.getUserId().equals(legacyUserId)) {
                businessMetrics.identityLegacyFallback("customer_read");
                return;
            }
        }
        if (RoleConstants.RESTAURANT_OWNER.equals(role)) {
            if (order.getCreatorPrincipalId() != null && principalId != null
                    && order.getCreatorPrincipalId().equals(principalId)) return;
            if (!principalOwnershipEnforced && order.getCreatorPrincipalId() == null
                    && order.getCreatorId().equals(legacyUserId)) {
                businessMetrics.identityLegacyFallback("restaurant_owner_read");
                return;
            }
        }
        if (RoleConstants.SHIPPER.equals(role) && legacyUserId != null && legacyUserId.equals(order.getShipperId())) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền xem đơn hàng này");
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId, String role, String reason) {
        return cancelOrder(orderId, userId, userId, role, reason);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long principalId, Long userId, String role, String reason) {
        // Lấy thông tin đơn hàng
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));

        // Kiểm tra quyền hủy đơn hàng
        validateCancelOrderPermission(order, principalId, userId, role);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            requireExactCancellationReplay(order, principalId, userId, reason);
            return orderMapper.orderToOrderResponse(order);
        }

        // Kiểm tra điều kiện hủy đơn hàng
        validateCancelOrderConditions(order, userId, role);

        // Lưu trạng thái cũ để gửi event
        String previousStatus = order.getStatus().name();

        // Cập nhật trạng thái thành CANCELLED
        order.getStatus().requireTransitionTo(OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledBy(userId);
        order.setCancelledByPrincipalId(principalId);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        businessMetrics.record("order_cancelled");

        // ✅ Publish the cancellation for Delivery and the refund boundary.  The
        // source is part of the durable event so a future provider rollout cannot
        // mistake an admin/customer exception for an automatic refund trigger.
        String cancellationSource = RoleConstants.ADMIN.equals(role) ? "ADMIN"
                : RoleConstants.RESTAURANT_OWNER.equals(role) ? "RESTAURANT" : "CUSTOMER";
        String reasonCode = RoleConstants.ADMIN.equals(role) ? "ADMIN_CANCELLED"
                : RoleConstants.RESTAURANT_OWNER.equals(role) ? "RESTAURANT_CANCELLED" : "CUSTOMER_CANCELLED";
        orderEventPublisher.publishOrderCancelledEvent(order, previousStatus, userId,
                cancellationSource, reasonCode);

        return orderMapper.orderToOrderResponse(order);
    }

    private void validateCancelOrderPermission(Order order, Long principalId, Long legacyUserId, String role) {
        // Admin có thể hủy bất kỳ đơn hàng nào
        if (RoleConstants.ADMIN.equals(role)) {
            return;
        }

        // User chỉ có thể hủy đơn hàng của mình
        if (RoleConstants.USER.equals(role)) {
            if (order.getUserPrincipalId() != null && principalId != null
                    && order.getUserPrincipalId().equals(principalId)) return;
            if (!principalOwnershipEnforced && order.getUserPrincipalId() == null
                    && order.getUserId().equals(legacyUserId)) {
                businessMetrics.identityLegacyFallback("customer_cancel");
                return;
            }
        }

        // Restaurant owner có thể hủy đơn hàng của nhà hàng mình
        if (RoleConstants.RESTAURANT_OWNER.equals(role)) {
            if (order.getCreatorPrincipalId() != null && principalId != null
                    && order.getCreatorPrincipalId().equals(principalId)) return;
            if (!principalOwnershipEnforced && order.getCreatorPrincipalId() == null
                    && order.getCreatorId().equals(legacyUserId)) {
                businessMetrics.identityLegacyFallback("restaurant_owner_cancel");
                return;
            }
        }

        throw new AccessDeniedException("Bạn không có quyền hủy đơn hàng này");
    }

    private void requireExactCancellationReplay(Order order, Long principalId, Long legacyUserId, String reason) {
        if ((order.getCancelledByPrincipalId() != null && Objects.equals(order.getCancelledByPrincipalId(), principalId)
                    || !principalOwnershipEnforced && order.getCancelledByPrincipalId() == null
                            && Objects.equals(order.getCancelledBy(), legacyUserId))
                && Objects.equals(order.getCancelReason(), reason)) {
            if (order.getCancelledByPrincipalId() == null) businessMetrics.identityLegacyFallback("cancel_replay");
            log.info("Order {} cancellation already applied by principal {}, skipping exact replay",
                    order.getId(), principalId);
            return;
        }
        throw new IllegalStateException("Order cancellation already exists with a different actor or reason");
    }

    private void validateCancelOrderConditions(Order order, Long userId, String role) {
        // Admin có thể hủy bất kỳ đơn nào
        if (RoleConstants.ADMIN.equals(role)) return;

        // Customer/restaurant owner chỉ có thể hủy trước pickup. Shipper cancellation
        // belongs to Delivery /cancel-assignment so availability and rematch converge.
        if (order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.FINDING_SHIPPER
                && order.getStatus() != OrderStatus.WAIT_SHIPPER_CONFIRM
                && order.getStatus() != OrderStatus.ASSIGNED) {
            throw new IllegalStateException("Không thể hủy đơn hàng ở trạng thái: " + order.getStatus());
        }
    }

    /**
     * ✅ Cập nhật order status khi không tìm được shipper
     */
    @Override
    @Transactional
    public void updateOrderStatusFromShipperNotFoundEvent(ShipperNotFoundEvent event) {
        try {
            log.info("🔄 Processing ShipperNotFoundEvent for order: {}, delivery: {}",
                    event.getOrderId(), event.getDeliveryId());

            // Tìm order theo orderId
            Order order = orderRepository.findByIdForUpdate(event.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order not found with id: " + event.getOrderId()));

            // Chỉ cập nhật nếu order đang ở trạng thái phù hợp (PENDING, CONFIRMED)
            if (order.getStatus() != OrderStatus.CONFIRMED
                    && order.getStatus() != OrderStatus.FINDING_SHIPPER
                    && order.getStatus() != OrderStatus.WAIT_SHIPPER_CONFIRM) {
                log.warn("⚠️ Order {} not in a matching status, current status: {}",
                        order.getId(), order.getStatus());
                return;
            }

            // Cập nhật status và note về việc không tìm được shipper
            OrderStatus previousStatus = order.getStatus();
            order.getStatus().requireTransitionTo(OrderStatus.SHIPPER_NOT_FOUND);
            order.setStatus(OrderStatus.SHIPPER_NOT_FOUND);
            order.setNotes("Không tìm được shipper sau " + event.getRetryAttempts() + " lần thử");

            orderRepository.save(order);

            // SHIPPER_NOT_FOUND is deliberately not rewritten to CANCELLED, but
            // it is still a deterministic pre-pickup compensation/refund trigger.
            orderEventPublisher.publishRefundEligibilityEvent(order, previousStatus.name(),
                    event.getReason());

            log.info("✅ Updated order {} status from {} to SHIPPER_NOT_FOUND after {} retry attempts",
                    order.getId(), previousStatus, event.getRetryAttempts());

            // Customer notification is derived from Delivery's canonical
            // delivery.status-updated outbox event. Order must not publish a
            // second notification for the same terminal matching outcome.

        } catch (Exception e) {
            log.error("💥 Error updating order status from ShipperNotFoundEvent for order: {}: {}",
                     event.getOrderId(), e.getMessage(), e);
            throw new IllegalStateException("Failed to apply shipper-not-found status", e);
        }
    }

}
