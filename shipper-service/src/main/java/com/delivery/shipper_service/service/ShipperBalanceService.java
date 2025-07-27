package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.request.BalanceTransactionRequest;
import com.delivery.shipper_service.dto.response.ShipperBalanceResponse;
import com.delivery.shipper_service.dto.response.ShipperTransactionResponse;

import java.math.BigDecimal;
import java.util.List;

public interface ShipperBalanceService {
    ShipperBalanceResponse getBalanceByShipperId(Long shipperId);
    ShipperBalanceResponse createBalanceForShipper(Long shipperId);
    ShipperBalanceResponse depositBalance(Long shipperId, BalanceTransactionRequest request);
    ShipperBalanceResponse withdrawBalance(Long shipperId, BalanceTransactionRequest request);
    ShipperBalanceResponse holdBalance(Long shipperId, BigDecimal amount, String description);
    ShipperBalanceResponse releaseBalance(Long shipperId, BigDecimal amount, String description);
    ShipperBalanceResponse earnFromOrder(Long shipperId, Long orderId, BigDecimal amount);
    List<ShipperTransactionResponse> getTransactionHistory(Long shipperId);
}
