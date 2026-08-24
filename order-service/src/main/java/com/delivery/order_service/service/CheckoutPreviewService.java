package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse.PreviewItemDetail;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse.PriceChangeInfo;
import com.delivery.order_service.exception.OrderDependencyUnavailableException;
import com.delivery.order_service.exception.ValidationException;
import com.delivery.order_service.config.OrderRestaurantCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.*;

/**
 * ✅ Service tính toán checkout preview — server là nguồn giá duy nhất.
 * Gọi restaurant-service để lấy giá canonical, tính shipping fee, áp coupon.
 */
@Slf4j
@Service
public class CheckoutPreviewService {

    private final WebClient webClient;
    private final ShippingFeeCalculationService shippingFeeService;
    private final String restaurantServiceUrl;
    private final String internalSecret;
    private final OrderRestaurantCircuitBreaker restaurantCircuitBreaker;
    private final VoucherCheckoutCapability voucherCheckoutCapability;
    @Autowired(required = false)
    private CheckoutReservationClient reservationClient;
    @Value("${app.order.voucher-checkout-enabled:false}") private boolean voucherCheckoutEnabled;
    @Value("${app.order.flashsale-checkout-enabled:false}") private boolean flashSaleCheckoutEnabled;
    @Value("${app.order.serviceability-enforcement-enabled:false}") private boolean serviceabilityEnforcementEnabled;
    @Value("${app.order.eta-window-enabled:false}") private boolean etaWindowEnabled;
    @Value("${routing.service.url:http://routing-service}") private String routingServiceUrl;

    @Autowired
    public CheckoutPreviewService(WebClient webClient,
                                  ShippingFeeCalculationService shippingFeeService,
                                  @Value("${restaurant.service.url}") String restaurantServiceUrl,
                                  @Value("${app.internal.secret:}") String internalSecret,
                                  OrderRestaurantCircuitBreaker restaurantCircuitBreaker,
                                  VoucherCheckoutCapability voucherCheckoutCapability) {
        this.webClient = webClient;
        this.shippingFeeService = shippingFeeService;
        this.restaurantServiceUrl = restaurantServiceUrl;
        this.internalSecret = internalSecret;
        this.restaurantCircuitBreaker = restaurantCircuitBreaker;
        this.voucherCheckoutCapability = voucherCheckoutCapability;
    }

    /** Source-compatible constructor for focused legacy tests/callers. */
    public CheckoutPreviewService(WebClient webClient,
                                  ShippingFeeCalculationService shippingFeeService,
                                  String restaurantServiceUrl,
                                  String internalSecret,
                                  OrderRestaurantCircuitBreaker restaurantCircuitBreaker) {
        this(webClient, shippingFeeService, restaurantServiceUrl, internalSecret,
                restaurantCircuitBreaker, new VoucherCheckoutCapability(false, ""));
    }

    /**
     * Tính toán checkout preview.
     * 1. Gọi restaurant-service lấy menu items + restaurant info
     * 2. Tính subtotal từ giá server
     * 3. Tính shipping fee theo khoảng cách
     * 4. Giữ discount bằng 0 trong COD MVP
     * 5. Trả về breakdown chi tiết
     */
    @SuppressWarnings("unchecked")
    public CheckoutPreviewResponse calculatePreview(CheckoutPreviewRequest request, Long principalId, Long userId) {
        if (request == null) {
            throw new ValidationException("Dữ liệu checkout không được để trống");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ValidationException("Checkout phải có ít nhất một sản phẩm");
        }
        if (request.getItems().size() > 50) {
            throw new ValidationException("Checkout không được vượt quá 50 sản phẩm");
        }
        validateDuplicateItems(request);
        log.info("📋 Calculating checkout preview for user={}, restaurant={}", userId, request.getRestaurantId());

        // Coupon codes are collected through Promotion first; the checkout
        // quote only accepts stable wallet IDs and never trusts a client code.
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            throw new ValidationException("Hãy lưu mã voucher vào ví trước khi báo giá checkout");
        }
        boolean hasFlashSale = request.getItems().stream().anyMatch(item -> item.getFlashSaleItemId() != null);
        List<Long> selectedVoucherIds = selectedVoucherIds(request);
        if (request.getVoucherId() != null && request.getSelectedVoucherIds() != null) {
            throw new ValidationException("Không được gửi đồng thời voucherId và selectedVoucherIds");
        }
        if (request.getSelectionMode() != null && !request.getSelectionMode().isBlank()
                && !"AUTO".equalsIgnoreCase(request.getSelectionMode())
                && !"MANUAL".equalsIgnoreCase(request.getSelectionMode())) {
            throw new ValidationException("Selection mode chỉ hỗ trợ AUTO hoặc MANUAL");
        }
        if (request.getSelectedVoucherIds() != null
                && request.getSelectedVoucherIds().size() == 1
                && (request.getSelectionMode() == null || request.getSelectionMode().isBlank())) {
            throw new ValidationException(
                    "Một voucher trong selectedVoucherIds phải đi kèm selectionMode rõ ràng");
        }
        if ("MANUAL".equalsIgnoreCase(request.getSelectionMode()) && selectedVoucherIds.isEmpty()) {
            throw new ValidationException("Manual voucher mode requires selected voucher IDs");
        }
        boolean hasVoucherSelection = !selectedVoucherIds.isEmpty()
                || request.getSelectionMode() != null || request.getVoucherId() != null;
        boolean stackedSelection = (request.getSelectionMode() != null && !request.getSelectionMode().isBlank())
                || (request.getSelectedVoucherIds() != null && !request.getSelectedVoucherIds().isEmpty());
        if (hasVoucherSelection && stackedSelection && !voucherCheckoutCapability.isEnabled(principalId))
            throw new ValidationException("Voucher stacking checkout is not enabled for this account");
        if (hasVoucherSelection && !stackedSelection && !voucherCheckoutEnabled)
            throw new ValidationException("Voucher checkout is disabled");
        if (hasFlashSale && !flashSaleCheckoutEnabled)
            throw new ValidationException("Flash-sale checkout is disabled");
        if (hasVoucherSelection && hasFlashSale)
            throw new ValidationException("Voucher và Flash Sale không được áp dụng cùng một đơn");
        if ((hasVoucherSelection || hasFlashSale) && reservationClient == null)
            throw new ValidationException("Checkout reservation capability is unavailable");

        // 1. Lấy canonical restaurant + menu item facts qua cùng internal
        // validation contract với create-order. Preview không được gọi public
        // catalog endpoint rồi tự suy luận vì path đó yếu hơn checkout boundary.
        Map<String, Object> validationData = fetchValidatedCheckoutFacts(request);
        Map<String, Object> restaurantInfo = requireMap(
                validationData.get("restaurantInfo"),
                "Restaurant service không trả restaurantInfo canonical");

        String restaurantName = requireNonBlankString(
                restaurantInfo.get("restaurantName"),
                "Restaurant service thiếu tên nhà hàng canonical");
        Double pickupLat = requireCoordinate(
                restaurantInfo.get("latitude"),
                8.0,
                24.0,
                "Restaurant service thiếu tọa độ pickup latitude canonical");
        Double pickupLng = requireCoordinate(
                restaurantInfo.get("longitude"),
                102.0,
                110.0,
                "Restaurant service thiếu tọa độ pickup longitude canonical");

        if (serviceabilityEnforcementEnabled) {
            Boolean serviceabilityEnabled = getBooleanValue(restaurantInfo.get("serviceabilityEnabled"));
            Boolean serviceable = getBooleanValue(restaurantInfo.get("serviceable"));
            if (!Boolean.TRUE.equals(serviceabilityEnabled) || !Boolean.TRUE.equals(serviceable)) {
                throw new ValidationException("Địa chỉ giao hàng hiện nằm ngoài vùng phục vụ");
            }
        }

        Integer prepMinutes = getIntegerValue(restaurantInfo.get("defaultPrepTimeMinutes"));
        if (prepMinutes == null) prepMinutes = 30;
        if (etaWindowEnabled && (prepMinutes < 1 || prepMinutes > 240)) {
            throw new ValidationException("Restaurant service thiếu prep time canonical");
        }

        Map<Long, ValidatedPreviewItem> menuItemMap = parseValidatedItems(validationData);
        CheckoutReservationClient.FlashQuote flashQuote = hasFlashSale
                ? reservationClient.quoteFlash(request.getRestaurantId(), request.getItems().stream().map(item -> {
                    com.delivery.order_service.dto.request.CreateOrderRequest.OrderItemRequest mapped =
                            new com.delivery.order_service.dto.request.CreateOrderRequest.OrderItemRequest();
                    mapped.setMenuItemId(item.getMenuItemId()); mapped.setFlashSaleItemId(item.getFlashSaleItemId());
                    mapped.setQuantity(item.getQuantity()); return mapped;
                }).toList()) : null;

        // 3. Map từng item trong request → giá server
        List<PreviewItemDetail> previewItems = new ArrayList<>();
        List<PriceChangeInfo> priceChanges = new ArrayList<>();
        List<Long> unavailableIds = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CheckoutPreviewRequest.PreviewItem reqItem : request.getItems()) {
            ValidatedPreviewItem serverItem = menuItemMap.get(reqItem.getMenuItemId());

            if (serverItem == null) {
                unavailableIds.add(reqItem.getMenuItemId());
                continue;
            }

            if (!serverItem.available()) {
                unavailableIds.add(reqItem.getMenuItemId());
                continue;
            }

            BigDecimal unitPrice = serverItem.price();
            if (reqItem.getFlashSaleItemId() != null) {
                CheckoutReservationClient.FlashLine line = flashQuote.byFlashSaleItemId()
                        .get(reqItem.getFlashSaleItemId());
                if (line == null || !reqItem.getMenuItemId().equals(line.menuItemId())
                        || !reqItem.getQuantity().equals(line.quantity()))
                    throw new ValidationException("Flash-sale quote does not match checkout item");
                unitPrice = line.unitPrice();
            }
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(reqItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            previewItems.add(PreviewItemDetail.builder()
                    .menuItemId(reqItem.getMenuItemId())
                    .menuItemName(serverItem.name())
                    .imageUrl(null)
                    .unitPrice(unitPrice)
                    .quantity(reqItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        if (!unavailableIds.isEmpty()) {
            throw new ValidationException("Checkout chứa món không khả dụng: " + unavailableIds);
        }

        // 4. Tính shipping fee
        BigDecimal shippingFee = shippingFeeService.calculateShippingFee(
                pickupLat, pickupLng,
                request.getDeliveryLat(), request.getDeliveryLng(),
                subtotal);

        EtaWindow etaWindow = etaWindowEnabled
                ? fetchEtaWindow(pickupLat, pickupLng, request.getDeliveryLat(), request.getDeliveryLng(), prepMinutes)
                : null;

        CheckoutReservationClient.PromotionQuote promotionQuote = null;
        CheckoutReservationClient.VoucherQuote legacyQuote = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getVoucherId() != null && request.getSelectedVoucherIds() == null
                && request.getSelectionMode() == null) {
            // Legacy single-voucher quote remains available while old clients
            // drain; it is never mixed with the stacked contract.
            legacyQuote = quoteVoucher(userId, principalId, request.getVoucherId(), request.getRestaurantId(),
                    subtotal, shippingFee);
            discountAmount = legacyQuote.discountAmount();
        } else if (hasVoucherSelection) {
            promotionQuote = reservationClient.quoteVouchers(userId, principalId, request.getRestaurantId(),
                    subtotal, shippingFee, selectedVoucherIds, request.getSelectionMode());
            discountAmount = promotionQuote.totalDiscount();
        }
        if (discountAmount.signum() < 0 || discountAmount.compareTo(subtotal.add(shippingFee)) > 0)
            throw new ValidationException("Voucher quote returned an invalid discount");

        BigDecimal itemDiscount = promotionQuote != null ? promotionQuote.itemDiscount()
                : legacyQuote != null ? legacyQuote.itemDiscount() : discountAmount;
        BigDecimal shippingDiscount = promotionQuote != null ? promotionQuote.shippingDiscount()
                : legacyQuote != null ? legacyQuote.shippingDiscount() : BigDecimal.ZERO;
        BigDecimal customerShippingFee = promotionQuote != null
                ? promotionQuote.customerShippingFee()
                : legacyQuote != null && legacyQuote.customerShippingFee() != null
                ? legacyQuote.customerShippingFee() : shippingFee.subtract(shippingDiscount).max(BigDecimal.ZERO);
        BigDecimal grossShippingFee = shippingFee;
        BigDecimal platformSubsidy = promotionQuote != null ? promotionQuote.platformSubsidy()
                : legacyQuote != null ? legacyQuote.platformSubsidy() : BigDecimal.ZERO;
        BigDecimal shopDiscount = promotionQuote != null ? promotionQuote.shopDiscount()
                : legacyQuote != null ? legacyQuote.shopDiscount() : BigDecimal.ZERO;
        BigDecimal totalPrice = subtotal.subtract(itemDiscount).add(customerShippingFee);
        if (totalPrice.compareTo(grossShippingFee) <= 0) {
            throw new ValidationException("Voucher phải để lại số tiền món dương cho đơn hàng");
        }

        log.info("✅ Checkout preview: subtotal={}, shipping={}, discount={}, total={}, items={}, unavailable={}",
                subtotal, shippingFee, discountAmount, totalPrice, previewItems.size(), unavailableIds.size());

        return CheckoutPreviewResponse.builder()
                .restaurantId(request.getRestaurantId())
                .restaurantName(restaurantName)
                .etaMinMinutes(etaWindow == null ? null : etaWindow.minMinutes())
                .etaMaxMinutes(etaWindow == null ? null : etaWindow.maxMinutes())
                .etaSource(etaWindow == null ? null : etaWindow.source())
                .serviceabilityZoneId(serviceabilityEnforcementEnabled
                        ? asLong(restaurantInfo.get("serviceabilityZoneId")) : null)
                .serviceabilityZoneRevision(serviceabilityEnforcementEnabled
                        ? asLong(restaurantInfo.get("serviceabilityZoneRevision")) : null)
                .items(previewItems)
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .totalPrice(totalPrice)
                .itemDiscount(itemDiscount)
                .shippingDiscount(shippingDiscount)
                .customerShippingFee(customerShippingFee)
                .grossShippingFee(grossShippingFee)
                .platformSubsidy(platformSubsidy)
                .shopDiscount(shopDiscount)
                .couponCode(request.getCouponCode())
                .couponMessage(null)
                .voucherId(request.getVoucherId())
                .selectedVoucherIds(promotionQuote == null ? selectedVoucherIds : promotionQuote.selectedVoucherIds())
                .selectionMode(request.getSelectionMode())
                .appliedVouchers(promotionQuote != null
                        ? toAppliedVouchers(promotionQuote)
                        : toAppliedVouchers(legacyQuote))
                .priceChanges(priceChanges)
                .unavailableItemIds(unavailableIds)
                .build();
    }

    /** Compatibility rail for existing callers/tests while JWT subject is still legacy profile ID. */
    public CheckoutPreviewResponse calculatePreview(CheckoutPreviewRequest request, Long userId) {
        return calculatePreview(request, userId, userId);
    }

    /**
     * Keep the legacy internal reservation contract available while identity
     * migration is in compatibility mode. Once principal and legacy IDs
     * differ, include the stable principal in the reservation request.
     */
    private CheckoutReservationClient.VoucherQuote quoteVoucher(
            Long userId, Long principalId, Long voucherId, Long restaurantId,
            BigDecimal subtotal, BigDecimal shippingFee) {
        if (principalId == null || java.util.Objects.equals(principalId, userId)) {
            return reservationClient.quoteVoucher(userId, voucherId, restaurantId, subtotal, shippingFee);
        }
        return reservationClient.quoteVoucher(userId, principalId, voucherId, restaurantId, subtotal, shippingFee);
    }

    @SuppressWarnings("unchecked")
    private EtaWindow fetchEtaWindow(double pickupLat, double pickupLng,
                                     double deliveryLat, double deliveryLng,
                                     int prepMinutes) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new OrderDependencyUnavailableException("routing-service",
                    "Order/routing internal credential chưa được cấu hình", null, 30);
        }
        try {
            Map<String, Object> response = webClient.post()
                    .uri(routingServiceUrl + "/internal/routing/v1/eta-window")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Internal-Token", internalSecret)
                    .bodyValue(Map.of(
                            "origin", Map.of("lat", pickupLat, "lng", pickupLng),
                            "destination", Map.of("lat", deliveryLat, "lng", deliveryLng),
                            "prepMinutes", prepMinutes))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) throw new IllegalStateException("empty ETA response");
            Integer min = getIntegerValue(response.get("minMinutes"));
            Integer max = getIntegerValue(response.get("maxMinutes"));
            String source = getStringValue(response.get("source"));
            if (min == null || max == null || min < 1 || max < min || source == null || source.isBlank()) {
                throw new IllegalStateException("invalid ETA response");
            }
            return new EtaWindow(min, max, source);
        } catch (Exception failure) {
            if (failure instanceof OrderDependencyUnavailableException dependency) throw dependency;
            throw new OrderDependencyUnavailableException("routing-service",
                    "Routing service tạm thời không khả dụng", failure, 30);
        }
    }

    private List<Long> selectedVoucherIds(CheckoutPreviewRequest request) {
        List<Long> ids = request.getSelectedVoucherIds() == null
                ? new ArrayList<>() : new ArrayList<>(request.getSelectedVoucherIds());
        if (request.getVoucherId() != null && ids.isEmpty()) ids.add(request.getVoucherId());
        if (ids.size() > 3 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw new ValidationException("Tối đa 3 voucher khác nhau, mỗi lớp một voucher");
        }
        return ids;
    }

    private List<CheckoutPreviewResponse.AppliedVoucherInfo> toAppliedVouchers(
            CheckoutReservationClient.PromotionQuote quote) {
        if (quote == null || quote.breakdownJson() == null) return List.of();
        return quote.breakdown().stream().map(line -> CheckoutPreviewResponse.AppliedVoucherInfo.builder()
                .voucherId(asLong(line.get("voucherId")))
                .code(line.get("voucherCode") == null ? text(line.get("code")) : text(line.get("voucherCode")))
                .layer(text(line.get("layer")))
                .fundingSource(text(line.get("fundingSource")))
                .discountBase(asDecimal(line.get("discountBase")))
                .discountAmount(asDecimal(line.get("discountAmount")))
                .build()).toList();
    }

    private List<CheckoutPreviewResponse.AppliedVoucherInfo> toAppliedVouchers(
            CheckoutReservationClient.VoucherQuote quote) {
        if (quote == null || quote.breakdown() == null) return List.of();
        return quote.breakdown().stream().map(line -> CheckoutPreviewResponse.AppliedVoucherInfo.builder()
                .voucherId(asLong(line.get("voucherId")))
                .code(line.get("voucherCode") == null ? text(line.get("code")) : text(line.get("voucherCode")))
                .layer(text(line.get("layer")))
                .fundingSource(text(line.get("fundingSource")))
                .discountBase(asDecimal(line.get("discountBase")))
                .discountAmount(asDecimal(line.get("discountAmount")))
                .build()).toList();
    }

    private String text(Object value) { return value == null ? null : value.toString(); }
    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? null : Long.valueOf(value.toString());
    }
    private BigDecimal asDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : value == null ? null : new BigDecimal(value.toString());
    }

    // ────────────────── Private helpers ──────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchValidatedCheckoutFacts(CheckoutPreviewRequest request) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new OrderDependencyUnavailableException("restaurant-service",
                    "Order/restaurant internal credential chưa được cấu hình", null, 30);
        }

        try {
            Map<String, Object> orderValidationRequest = Map.of(
                    "restaurantId", request.getRestaurantId(),
                    "deliveryLat", request.getDeliveryLat(),
                    "deliveryLng", request.getDeliveryLng(),
                    "items", request.getItems().stream()
                            .map(item -> Map.of(
                                    "menuItemId", item.getMenuItemId(),
                                    "quantity", item.getQuantity()))
                            .toList());

            Map<String, Object> response = restaurantCircuitBreaker.execute(() -> webClient
                    .post()
                    .uri(restaurantServiceUrl + "/api/restaurants/validate/order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Internal-Token", internalSecret)
                    .bodyValue(orderValidationRequest)
                    .retrieve().bodyToMono(Map.class)
                    .timeout(restaurantCircuitBreaker.timeout())
                    .block());

            if (response == null) {
                throw new OrderDependencyUnavailableException("restaurant-service",
                        "Restaurant service trả về response rỗng");
            }
            Object rawData = response.get("data");
            if (!(rawData instanceof Map<?, ?>)) {
                throw new OrderDependencyUnavailableException("restaurant-service",
                        "Restaurant service trả response không đúng contract");
            }
            Map<String, Object> data = requireMap(rawData,
                    "Response từ restaurant service không hợp lệ");
            Integer status = getIntegerValue(response.get("status"));
            if (status == null) {
                throw new OrderDependencyUnavailableException("restaurant-service",
                        "Restaurant service trả status không hợp lệ");
            }
            if (status == null || status != 1) {
                throw new ValidationException("Dữ liệu checkout không hợp lệ: "
                        + validationMessage(data));
            }

            return data;
        } catch (Exception e) {
            if (e instanceof ValidationException validationException) {
                throw validationException;
            }
            if (e instanceof OrderDependencyUnavailableException dependencyUnavailable) {
                throw dependencyUnavailable;
            }
            if (e instanceof WebClientResponseException responseException
                    && responseException.getStatusCode().is4xxClientError()) {
                throw new ValidationException("Không thể xác thực thông tin restaurant/menu items");
            }
            log.error("❌ Failed to validate checkout for restaurant {}: {}",
                    request.getRestaurantId(), e.getMessage());
            throw new OrderDependencyUnavailableException("restaurant-service",
                    "Restaurant service tạm thời không khả dụng", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ValidatedPreviewItem> parseValidatedItems(Map<String, Object> validationData) {
        Object rawItems = validationData.get("itemValidations");
        if (!(rawItems instanceof List<?> itemValidations)) {
            throw new ValidationException("Restaurant service không trả dữ liệu canonical của món ăn");
        }

        Map<Long, ValidatedPreviewItem> items = new HashMap<>();
        for (Object rawItem : itemValidations) {
            if (!(rawItem instanceof Map<?, ?> item)) {
                throw new ValidationException("Restaurant service trả item validation không hợp lệ");
            }
            Long menuItemId = getLongValue(item.get("menuItemId"));
            String name = getStringValue(item.get("menuItemName"));
            BigDecimal price = getBigDecimalValue(item.get("actualPrice"));
            boolean available = Boolean.TRUE.equals(getBooleanValue(item.get("isAvailable")))
                    && !Boolean.FALSE.equals(getBooleanValue(item.get("hasEnoughStock")))
                    && name != null
                    && !name.isBlank()
                    && price != null
                    && price.signum() > 0;
            if (menuItemId != null) {
                items.put(menuItemId, new ValidatedPreviewItem(menuItemId, name, price, available));
            }
        }
        return items;
    }

    private void validateDuplicateItems(CheckoutPreviewRequest request) {
        Set<Long> menuItemIds = new HashSet<>();
        Set<Long> flashSaleItemIds = new HashSet<>();
        for (CheckoutPreviewRequest.PreviewItem item : request.getItems()) {
            if (item == null || item.getMenuItemId() == null) {
                throw new ValidationException("Checkout chứa sản phẩm không hợp lệ");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 99
                    || (item.getFlashSaleItemId() != null && item.getFlashSaleItemId() <= 0)) {
                throw new ValidationException("Checkout chứa số lượng hoặc flash-sale item không hợp lệ");
            }
            if (!menuItemIds.add(item.getMenuItemId())) {
                throw new ValidationException("Menu Item ID bị trùng trong checkout preview");
            }
            if (item.getFlashSaleItemId() != null && !flashSaleItemIds.add(item.getFlashSaleItemId()))
                throw new ValidationException("Flash Sale Item ID bị trùng trong checkout preview");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ValidationException(message);
        }
        return (Map<String, Object>) map;
    }

    private String requireNonBlankString(Object value, String message) {
        String result = getStringValue(value);
        if (result == null || result.isBlank()) {
            throw new ValidationException(message);
        }
        return result;
    }

    private Double requireCoordinate(Object value, double min, double max, String message) {
        Double result = getDoubleValue(value);
        if (result == null || !Double.isFinite(result) || result < min || result > max) {
            throw new ValidationException(message);
        }
        return result;
    }

    private String validationMessage(Map<String, Object> data) {
        Object errors = data.get("errors");
        if (!(errors instanceof List<?> validationErrors) || validationErrors.isEmpty()) {
            return "Restaurant/menu item validation thất bại";
        }
        List<String> messages = new ArrayList<>();
        for (Object rawError : validationErrors) {
            if (rawError instanceof Map<?, ?> error) {
                String message = getStringValue(error.get("message"));
                if (message != null && !message.isBlank()) {
                    messages.add(message);
                }
            }
        }
        return messages.isEmpty()
                ? "Restaurant/menu item validation thất bại"
                : String.join(", ", messages);
    }

    private String getStringValue(Object val) {
        return val != null ? val.toString() : null;
    }

    private Double getDoubleValue(Object val) {
        if (val == null) return null;
        try { return Double.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer getIntegerValue(Object val) {
        if (val == null) return null;
        try { return Integer.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Long getLongValue(Object val) {
        if (val == null) return null;
        try { return Long.valueOf(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal getBigDecimalValue(Object val) {
        if (val == null) return null;
        try { return new BigDecimal(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private record EtaWindow(int minMinutes, int maxMinutes, String source) {
    }

    private Boolean getBooleanValue(Object val) {
        if (val == null) return null;
        if (val instanceof Boolean bool) return bool;
        return Boolean.valueOf(val.toString());
    }

    private record ValidatedPreviewItem(
            Long menuItemId,
            String name,
            BigDecimal price,
            boolean available) {
    }
}
