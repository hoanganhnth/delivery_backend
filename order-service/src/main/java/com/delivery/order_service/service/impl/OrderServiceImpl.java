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
import com.delivery.order_service.exception.ResourceNotFoundException;
import com.delivery.order_service.mapper.OrderMapper;
import com.delivery.order_service.repository.OrderItemRepository;
import com.delivery.order_service.repository.OrderRepository;
import com.delivery.order_service.service.OrderEventPublisher;
import com.delivery.order_service.service.OrderService;
import com.delivery.order_service.service.OrderValidationService;
import com.delivery.order_service.service.ShippingFeeCalculationService;
import com.delivery.order_service.metrics.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    public OrderServiceImpl(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           OrderMapper orderMapper,
                           OrderEventPublisher orderEventPublisher,
                           OrderValidationService orderValidationService,
                           ShippingFeeCalculationService shippingFeeCalculationService,
                           BusinessMetrics businessMetrics) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderMapper = orderMapper;
        this.orderEventPublisher = orderEventPublisher;
        this.orderValidationService = orderValidationService;
        this.shippingFeeCalculationService = shippingFeeCalculationService;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long userId, String role) {
        if (!RoleConstants.USER.equals(role)) {
            throw new AccessDeniedException("Chỉ khách hàng được tạo đơn hàng");
        }
        // ✅ Validate request + lấy canonical restaurant data từ server (1 lần duy nhất gọi restaurant-service)
        ValidatedOrderData validated = orderValidationService.validateCreateOrderRequest(request, userId);

        if (validated == null || validated.creatorId() == null) {
            throw new ResourceNotFoundException(
                    "Không thể lấy thông tin nhà hàng. Restaurant ID: " + request.getRestaurantId()
            );
        }

        log.info("✅ Restaurant validated from server. creatorId={}, name={}",
                validated.creatorId(), validated.restaurantName());

        // ✅ Mapper chỉ copy: restaurantId, deliveryAddress, deliveryLat/Lng,
        //   customerName, customerPhone, paymentMethod, notes.
        //   Các trường nhà hàng sẽ được set rõ ràng từ ValidatedOrderData bên dưới.
        Order order = orderMapper.createOrderRequestToOrder(request);
        order.setUserId(userId);

        // ✅ Set canonical restaurant data từ server — không dùng bất cứ dữ liệu nào từ client
        order.setCreatorId(validated.creatorId());
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

        // Tính toán giá trị hoàn toàn từ giá canonical của restaurant-service.
        BigDecimal subtotal = request.getItems().stream()
                .map(item -> requireCanonicalItem(canonicalItems, item.getMenuItemId()).price()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotalPrice(subtotal);
        order.setDiscountAmount(BigDecimal.ZERO);

        // ✅ Tính phí ship động theo khoảng cách
        BigDecimal shippingFee = shippingFeeCalculationService.calculateShippingFee(
            validated.pickupLat(),
            validated.pickupLng(),
            request.getDeliveryLat(),
            request.getDeliveryLng(),
            subtotal
        );
        order.setShippingFee(shippingFee);

        order.setTotalPrice(subtotal.add(shippingFee).subtract(order.getDiscountAmount()));

        // Lưu order
        Order savedOrder = orderRepository.save(order);

        log.info("✅ Order created: id={}, restaurantId={}, creatorId={}, totalPrice={}",
                savedOrder.getId(), savedOrder.getRestaurantId(),
                savedOrder.getCreatorId(), savedOrder.getTotalPrice());

        // Tạo order items
        List<OrderItem> orderItems = request.getItems().stream()
            .map(itemRequest -> {
                ValidatedOrderData.ValidatedItemData canonicalItem =
                        requireCanonicalItem(canonicalItems, itemRequest.getMenuItemId());
                OrderItem orderItem = orderMapper.orderItemRequestToOrderItem(itemRequest);
                orderItem.setMenuItemName(canonicalItem.menuItemName());
                orderItem.setPrice(canonicalItem.price());
                orderItem.setOrder(savedOrder);
                return orderItem;
            })
            .toList();

        orderItemRepository.saveAll(orderItems);
        savedOrder.setItems(orderItems);

        // ✅ Publish OrderCreatedEvent to Kafka for Delivery Service
        orderEventPublisher.publishOrderCreatedEvent(savedOrder);
        businessMetrics.record("order_created");

        return orderMapper.orderToOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, Long userId, String role) {
        Order order = findOrderById(id);

        // Kiểm tra quyền xem
        validateViewPermission(order, userId, role);

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
    public Page<OrderResponse> getOrdersByRestaurantOwner(Long ownerId, Long userId, String role, Pageable pageable) {
        // Chỉ admin hoặc chính restaurant owner mới được xem
        if (!RoleConstants.ADMIN.equals(role)) {
            if (!RoleConstants.RESTAURANT_OWNER.equals(role)) {
                throw new AccessDeniedException("Bạn không có quyền xem đơn hàng của chủ nhà hàng");
            }
            // Restaurant owner chỉ xem được đơn hàng của chính mình
            if (!ownerId.equals(userId)) {
                throw new AccessDeniedException("Bạn chỉ có thể xem đơn hàng của nhà hàng mình sở hữu");
            }
        }

        // ✅ Query trực tiếp từ bảng orders theo creatorId (không cần gọi Restaurant Service)
        log.info("📋 Getting orders for restaurant owner (creatorId): {}", ownerId);

        Page<Order> orders = orderRepository.findByCreatorIdOrderByCreatedAtDesc(ownerId, pageable);
        log.info("✅ Found {} orders for restaurant owner {}", orders.getTotalElements(), ownerId);

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

    private void validateViewPermission(Order order, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể xem tất cả
        }

        if (RoleConstants.USER.equals(role) && order.getUserId().equals(userId)) {
            return;
        }
        if (RoleConstants.RESTAURANT_OWNER.equals(role) && order.getCreatorId().equals(userId)) {
            return;
        }
        if (RoleConstants.SHIPPER.equals(role) && userId.equals(order.getShipperId())) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền xem đơn hàng này");
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId, String role, String reason) {
        // Lấy thông tin đơn hàng
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));

        // Kiểm tra quyền hủy đơn hàng
        validateCancelOrderPermission(order, userId, role);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            requireExactCancellationReplay(order, userId, reason);
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
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        businessMetrics.record("order_cancelled");

        // ✅ Publish OrderCancelledEvent để thông báo delivery service ngừng tìm shipper
        orderEventPublisher.publishOrderCancelledEvent(order, previousStatus, userId);

        return orderMapper.orderToOrderResponse(order);
    }

    private void validateCancelOrderPermission(Order order, Long userId, String role) {
        // Admin có thể hủy bất kỳ đơn hàng nào
        if (RoleConstants.ADMIN.equals(role)) {
            return;
        }

        // User chỉ có thể hủy đơn hàng của mình
        if (RoleConstants.USER.equals(role) && order.getUserId().equals(userId)) {
            return;
        }

        // Restaurant owner có thể hủy đơn hàng của nhà hàng mình
        if (RoleConstants.RESTAURANT_OWNER.equals(role) && order.getCreatorId().equals(userId)) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền hủy đơn hàng này");
    }

    private void requireExactCancellationReplay(Order order, Long userId, String reason) {
        if (Objects.equals(order.getCancelledBy(), userId)
                && Objects.equals(order.getCancelReason(), reason)) {
            log.info("Order {} cancellation already applied by actor {}, skipping exact replay",
                    order.getId(), userId);
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
