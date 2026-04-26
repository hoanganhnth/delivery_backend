package com.delivery.order_service.service.impl;

import com.delivery.order_service.client.RestaurantClient;
import com.delivery.order_service.common.constants.RoleConstants;
import com.delivery.order_service.dto.internal.ValidatedOrderData;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.request.UpdateOrderRequest;
import com.delivery.order_service.dto.response.OrderResponse;
import com.delivery.order_service.dto.event.ShipperNotFoundEvent;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.entity.OrderItem;
import com.delivery.order_service.exception.AccessDeniedException;
import com.delivery.order_service.exception.ResourceNotFoundException;
import com.delivery.order_service.mapper.OrderMapper;
import com.delivery.order_service.repository.OrderItemRepository;
import com.delivery.order_service.repository.OrderRepository;
import com.delivery.order_service.service.OrderEventPublisher;
import com.delivery.order_service.service.OrderService;
import com.delivery.order_service.service.OrderValidationService;
import com.delivery.order_service.service.ShippingFeeCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderValidationService orderValidationService;
    private final ShippingFeeCalculationService shippingFeeCalculationService;

    public OrderServiceImpl(OrderRepository orderRepository, 
                           OrderItemRepository orderItemRepository,
                           OrderMapper orderMapper,
                           OrderEventPublisher orderEventPublisher,
                           OrderValidationService orderValidationService,
                           ShippingFeeCalculationService shippingFeeCalculationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderMapper = orderMapper;
        this.orderEventPublisher = orderEventPublisher;
        this.orderValidationService = orderValidationService;
        this.shippingFeeCalculationService = shippingFeeCalculationService;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long userId, String role) {
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
        
        // Tính toán giá trị
        BigDecimal subtotal = calculateSubtotal(request.getItems());
        order.setSubtotalPrice(subtotal);
        order.setDiscountAmount(BigDecimal.ZERO);
        
        // ✅ Tính phí ship động theo khoảng cách
        BigDecimal shippingFee = shippingFeeCalculationService.calculateShippingFee(
            request.getPickupLat(),
            request.getPickupLng(),
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
                OrderItem orderItem = orderMapper.orderItemRequestToOrderItem(itemRequest);
                orderItem.setOrder(savedOrder);
                return orderItem;
            })
            .toList();
        
        orderItemRepository.saveAll(orderItems);
        savedOrder.setItems(orderItems);
        
        // ✅ Publish OrderCreatedEvent to Kafka for Delivery Service
        orderEventPublisher.publishOrderCreatedEvent(savedOrder);
        
        return orderMapper.orderToOrderResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, UpdateOrderRequest request, Long userId, String role) {
        Order order = findOrderById(id);
        
        // Kiểm tra quyền cập nhật
        validateUpdatePermission(order, userId, role);
        
        // Cập nhật order
        orderMapper.updateOrderFromRequest(request, order);
        
        Order updatedOrder = orderRepository.save(order);
        return orderMapper.orderToOrderResponse(updatedOrder);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id, Long userId, String role) {
        Order order = findOrderById(id);
        
        // Chỉ cho phép xóa nếu là chủ sở hữu hoặc admin
        if (!RoleConstants.ADMIN.equals(role) && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền xóa đơn hàng này");
        }
        
        // Chỉ cho phép xóa nếu đơn hàng chưa được xử lý
        if (!"PENDING".equals(order.getStatus()) && !"CANCELLED".equals(order.getStatus())) {
            throw new AccessDeniedException("Không thể xóa đơn hàng đã được xử lý");
        }
        
        // Xóa order items trước
        orderItemRepository.deleteByOrderId(id);
        
        // Xóa order
        orderRepository.delete(order);
    }

    @Override
    public OrderResponse getOrderById(Long id, Long userId, String role) {
        Order order = findOrderById(id);
        
        // Kiểm tra quyền xem
        validateViewPermission(order, userId, role);
        
        return orderMapper.orderToOrderResponse(order);
    }

    @Override
    public List<OrderResponse> getOrdersByUser(Long userId, String role) {
        // Chỉ cho phép xem đơn hàng của chính mình hoặc admin
        if (!RoleConstants.ADMIN.equals(role)) {
            List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
            return orderMapper.ordersToOrderResponses(orders);
        } else {
            List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
            return orderMapper.ordersToOrderResponses(orders);
        }
    }

    @Override
    public List<OrderResponse> getOrdersByRestaurant(Long restaurantId, Long userId, String role) {
        // Chỉ admin hoặc restaurant owner mới được xem
        if (!RoleConstants.ADMIN.equals(role) && !RoleConstants.RESTAURANT_OWNER.equals(role)) {
            throw new AccessDeniedException("Bạn không có quyền xem đơn hàng của nhà hàng");
        }
        
        List<Order> orders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        return orderMapper.ordersToOrderResponses(orders);
    }
    
    @Override
    public List<OrderResponse> getOrdersByRestaurantOwner(Long ownerId, Long userId, String role) {
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
        
        List<Order> orders = orderRepository.findByCreatorIdOrderByCreatedAtDesc(ownerId);
        log.info("✅ Found {} orders for restaurant owner {}", orders.size(), ownerId);
        
        return orderMapper.ordersToOrderResponses(orders);
    }

    @Override
    public List<OrderResponse> getOrdersByShipper(Long shipperId, Long userId, String role) {
        // Chỉ admin hoặc shipper mới được xem
        if (!RoleConstants.ADMIN.equals(role) && !RoleConstants.SHIPPER.equals(role)) {
            throw new AccessDeniedException("Bạn không có quyền xem đơn hàng của shipper");
        }
        
        List<Order> orders = orderRepository.findByShipperIdOrderByCreatedAtDesc(shipperId);
        return orderMapper.ordersToOrderResponses(orders);
    }

    @Override
    public List<OrderResponse> getOrdersByStatus(String status, Long userId, String role) {
        // Admin có thể xem tất cả, user chỉ xem của mình
        if (RoleConstants.ADMIN.equals(role)) {
            List<Order> orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
            return orderMapper.ordersToOrderResponses(orders);
        } else {
            List<Order> orders = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
            return orderMapper.ordersToOrderResponses(orders);
        }
    }

    @Override
    public List<OrderResponse> getAllOrders(Long userId, String role) {
        // Chỉ admin mới được xem tất cả đơn hàng
        if (!RoleConstants.ADMIN.equals(role)) {
            throw new AccessDeniedException("Bạn không có quyền xem tất cả đơn hàng");
        }
        
        List<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        return orderMapper.ordersToOrderResponses(orders);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, String status, Long userId, String role) {
        Order order = findOrderById(id);
        
        // Kiểm tra quyền cập nhật trạng thái
        validateStatusUpdatePermission(order, status, userId, role);
        
        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        
        return orderMapper.orderToOrderResponse(updatedOrder);
    }

    @Override
    @Transactional
    public OrderResponse assignShipper(Long orderId, Long shipperId, Long userId, String role) {
        Order order = findOrderById(orderId);
        
        // Chỉ admin mới được phân công shipper
        if (!RoleConstants.ADMIN.equals(role)) {
            throw new AccessDeniedException("Bạn không có quyền phân công shipper");
        }                                                       
        
        order.setShipperId(shipperId);
        Order updatedOrder = orderRepository.save(order);
        
        return orderMapper.orderToOrderResponse(updatedOrder);
    }

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));
    }

    private BigDecimal calculateSubtotal(List<CreateOrderRequest.OrderItemRequest> items) {
        return items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateUpdatePermission(Order order, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể cập nhật tất cả
        }
        
        // User chỉ có thể cập nhật đơn hàng của mình và chỉ khi đang pending
        if (order.getUserId().equals(userId) && "PENDING".equals(order.getStatus())) {
            return;
        }
        
        throw new AccessDeniedException("Bạn không có quyền cập nhật đơn hàng này");
    }

    private void validateViewPermission(Order order, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể xem tất cả
        }
        
        // User chỉ có thể xem đơn hàng của mình
        if (order.getUserId().equals(userId)) {
            return;
        }
        
        throw new AccessDeniedException("Bạn không có quyền xem đơn hàng này");
    }

    private void validateStatusUpdatePermission(Order order, String newStatus, Long userId, String role) {
        if (RoleConstants.ADMIN.equals(role)) {
            return; // Admin có thể cập nhật tất cả trạng thái
        }
        
        // User chỉ có thể hủy đơn hàng của mình
        if (order.getUserId().equals(userId) && "CANCELLED".equals(newStatus) && "PENDING".equals(order.getStatus())) {
            return;
        }
        
        throw new AccessDeniedException("Bạn không có quyền cập nhật trạng thái đơn hàng này");
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId, String role) {
        // Lấy thông tin đơn hàng
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));
        
        // Kiểm tra quyền hủy đơn hàng
        validateCancelOrderPermission(order, userId, role);
        
        // Kiểm tra điều kiện hủy đơn hàng
        validateCancelOrderConditions(order);
        
        // Lưu trạng thái cũ để gửi event
        String previousStatus = order.getStatus();
        
        // Cập nhật trạng thái thành CANCELLED
        order.setStatus("CANCELLED");
        order = orderRepository.save(order);
        
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
        if (order.getUserId().equals(userId)) {
            return;
        }
        
        // Restaurant owner có thể hủy đơn hàng của nhà hàng mình
        if (RoleConstants.RESTAURANT_OWNER.equals(role) && order.getCreatorId().equals(userId)) {
            return;
        }
        
        throw new AccessDeniedException("Bạn không có quyền hủy đơn hàng này");
    }
    
    private void validateCancelOrderConditions(Order order) {
        // Chỉ cho phép hủy đơn hàng ở trạng thái PENDING hoặc CONFIRMED
        if (!"PENDING".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể hủy đơn hàng ở trạng thái PENDING hoặc CONFIRMED. Trạng thái hiện tại: " + order.getStatus());
        }
        
        // Không cho phép hủy nếu đã có shipper được gán
        if (order.getShipperId() != null) {
            throw new IllegalStateException("Không thể hủy đơn hàng đã được gán cho shipper");
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
            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order not found with id: " + event.getOrderId()));
            
            // Chỉ cập nhật nếu order đang ở trạng thái phù hợp (PENDING, CONFIRMED)
            if (!"PENDING".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
                log.warn("⚠️ Order {} not in PENDING/CONFIRMED status, current status: {}", 
                        order.getId(), order.getStatus());
                return;
            }
            
            // Cập nhật status và note về việc không tìm được shipper
            String previousStatus = order.getStatus();
            order.setStatus("SHIPPER_NOT_FOUND");
            order.setNotes("Không tìm được shipper sau " + event.getRetryAttempts() + " lần thử");
            
            orderRepository.save(order);
            
            log.info("✅ Updated order {} status from {} to SHIPPER_NOT_FOUND after {} retry attempts", 
                    order.getId(), previousStatus, event.getRetryAttempts());
            
            // TODO: Có thể thêm logic để:
            // 1. Notify customer về việc không tìm được shipper
            // 2. Suggest alternative solutions (tăng tip, extend search radius)
            // 3. Auto-retry sau một khoảng thời gian
            
        } catch (Exception e) {
            log.error("💥 Error updating order status from ShipperNotFoundEvent for order: {}: {}", 
                     event.getOrderId(), e.getMessage(), e);
        }
    }
}
