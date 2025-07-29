package com.delivery.order_service.service.impl;

import com.delivery.order_service.common.constants.RoleConstants;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.request.UpdateOrderRequest;
import com.delivery.order_service.dto.response.OrderResponse;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.entity.OrderItem;
import com.delivery.order_service.exception.AccessDeniedException;
import com.delivery.order_service.exception.ResourceNotFoundException;
import com.delivery.order_service.mapper.OrderMapper;
import com.delivery.order_service.repository.OrderItemRepository;
import com.delivery.order_service.repository.OrderRepository;
import com.delivery.order_service.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository, 
                           OrderItemRepository orderItemRepository,
                           OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long userId, String role) {
        // Tạo order chính
        Order order = orderMapper.createOrderRequestToOrder(request);
        order.setUserId(userId);
        
        // Tính toán giá trị
        BigDecimal subtotal = calculateSubtotal(request.getItems());
        order.setSubtotalPrice(subtotal);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.valueOf(15000)); // Default shipping fee
        order.setTotalPrice(subtotal.add(order.getShippingFee()));
        
        // Lưu order
        Order savedOrder = orderRepository.save(order);
        
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
}
