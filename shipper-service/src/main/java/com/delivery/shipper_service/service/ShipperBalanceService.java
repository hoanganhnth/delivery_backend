package com.delivery.shipper_service.service;

import com.delivery.shipper_service.dto.request.BalanceTransactionRequest;
import com.delivery.shipper_service.dto.response.ShipperBalanceResponse;
import com.delivery.shipper_service.dto.response.ShipperTransactionResponse;

import java.math.BigDecimal;
import java.util.List;

public interface ShipperBalanceService {
    // Operations based on userId (from X-User-Id header)
    ShipperBalanceResponse getBalanceByUserId(Long userId);
    ShipperBalanceResponse createBalanceForUserId(Long userId);
    ShipperBalanceResponse depositBalanceByUserId(Long userId, BalanceTransactionRequest request);
    ShipperBalanceResponse withdrawBalanceByUserId(Long userId, BalanceTransactionRequest request);
    ShipperBalanceResponse holdBalanceByUserId(Long userId, BigDecimal amount, String description);
    ShipperBalanceResponse releaseBalanceByUserId(Long userId, BigDecimal amount, String description);
    ShipperBalanceResponse earnFromOrderByUserId(Long userId, Long orderId, BigDecimal amount);
    List<ShipperTransactionResponse> getTransactionHistoryByUserId(Long userId);
}
