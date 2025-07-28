package com.delivery.shipper_service.service.impl;

import com.delivery.shipper_service.dto.request.BalanceTransactionRequest;
import com.delivery.shipper_service.dto.response.ShipperBalanceResponse;
import com.delivery.shipper_service.dto.response.ShipperTransactionResponse;
import com.delivery.shipper_service.entity.ShipperBalance;
import com.delivery.shipper_service.entity.ShipperTransaction;
import com.delivery.shipper_service.exception.ResourceNotFoundException;
import com.delivery.shipper_service.mapper.ShipperBalanceMapper;
import com.delivery.shipper_service.mapper.ShipperTransactionMapper;
import com.delivery.shipper_service.repository.ShipperBalanceRepository;
import com.delivery.shipper_service.repository.ShipperTransactionRepository;
import com.delivery.shipper_service.service.ShipperBalanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShipperBalanceServiceImpl implements ShipperBalanceService {

    private final ShipperBalanceRepository shipperBalanceRepository;
    private final ShipperTransactionRepository shipperTransactionRepository;
    private final ShipperBalanceMapper shipperBalanceMapper;
    private final ShipperTransactionMapper shipperTransactionMapper;

    public ShipperBalanceServiceImpl(ShipperBalanceRepository shipperBalanceRepository,
                                     ShipperTransactionRepository shipperTransactionRepository,
                                     ShipperBalanceMapper shipperBalanceMapper,
                                     ShipperTransactionMapper shipperTransactionMapper) {
        this.shipperBalanceRepository = shipperBalanceRepository;
        this.shipperTransactionRepository = shipperTransactionRepository;
        this.shipperBalanceMapper = shipperBalanceMapper;
        this.shipperTransactionMapper = shipperTransactionMapper;
    }

    // === Private helper methods (used by userId-based methods) ===
    @Transactional
    private ShipperBalanceResponse createBalanceForUser(Long userId) {
        // Check if balance already exists
        if (shipperBalanceRepository.findByShipperId(userId).isPresent()) {
            throw new IllegalArgumentException("Shipper đã có tài khoản số dư");
        }

        ShipperBalance balance = new ShipperBalance();
        balance.setShipperId(userId);
        balance.setBalance(BigDecimal.ZERO);
        balance.setHoldingBalance(BigDecimal.ZERO);
        balance.setUpdatedAt(LocalDateTime.now());

        ShipperBalance savedBalance = shipperBalanceRepository.save(balance);
        return shipperBalanceMapper.toResponse(savedBalance);
    }

    @Transactional
    private ShipperBalanceResponse depositBalance(Long userId, BalanceTransactionRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }

        // Get or create balance
        ShipperBalance balance = getOrCreateBalance(userId);
        balance.addBalance(request.getAmount());
        ShipperBalance savedBalance = shipperBalanceRepository.save(balance);

        // Create transaction record
        ShipperTransaction transaction = new ShipperTransaction();
        transaction.setShipperId(userId);
        transaction.setTransactionType(ShipperTransaction.TransactionType.DEPOSIT);
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Nạp tiền vào tài khoản");
        transaction.setCreatedAt(LocalDateTime.now());
        shipperTransactionRepository.save(transaction);

        return shipperBalanceMapper.toResponse(savedBalance);
    }

    @Transactional
    private ShipperBalanceResponse withdrawBalance(Long userId, BalanceTransactionRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0");
        }

        // Get balance
        ShipperBalance balance = shipperBalanceRepository.findByShipperId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy số dư của shipper với userId: " + userId));

        // Check sufficient balance
        if (balance.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("Số dư không đủ để thực hiện giao dịch");
        }

        balance.deductBalance(request.getAmount());
        ShipperBalance savedBalance = shipperBalanceRepository.save(balance);

        // Create transaction record
        ShipperTransaction transaction = new ShipperTransaction();
        transaction.setShipperId(userId);
        transaction.setTransactionType(ShipperTransaction.TransactionType.WITHDRAW);
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Rút tiền từ tài khoản");
        transaction.setCreatedAt(LocalDateTime.now());
        shipperTransactionRepository.save(transaction);

        return shipperBalanceMapper.toResponse(savedBalance);
    }

    @Transactional
    private ShipperBalanceResponse holdBalance(Long userId, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giữ phải lớn hơn 0");
        }

        ShipperBalance balance = shipperBalanceRepository.findByShipperId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy số dư của shipper với userId: " + userId));

        if (balance.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Số dư không đủ để giữ");
        }

        // Hold money: move from balance to holding balance
        balance.deductBalance(amount);
        balance.addHoldingBalance(amount);
        ShipperBalance savedBalance = shipperBalanceRepository.save(balance);

        // Create transaction record
        ShipperTransaction transaction = new ShipperTransaction();
        transaction.setShipperId(userId);
        transaction.setTransactionType(ShipperTransaction.TransactionType.HOLD);
        transaction.setAmount(amount);
        transaction.setDescription(description != null ? description : "Giữ số dư");
        transaction.setCreatedAt(LocalDateTime.now());
        shipperTransactionRepository.save(transaction);

        return shipperBalanceMapper.toResponse(savedBalance);
    }

    @Transactional
    private ShipperBalanceResponse releaseBalance(Long userId, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giải phóng phải lớn hơn 0");
        }

        ShipperBalance balance = shipperBalanceRepository.findByShipperId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy số dư của shipper với userId: " + userId));

        if (balance.getHoldingBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Số dư giữ không đủ để giải phóng");
        }

        // Release money: move from holding balance back to balance
        balance.deductHoldingBalance(amount);
        balance.addBalance(amount);
        ShipperBalance savedBalance = shipperBalanceRepository.save(balance);

        // Create transaction record
        ShipperTransaction transaction = new ShipperTransaction();
        transaction.setShipperId(userId);
        transaction.setTransactionType(ShipperTransaction.TransactionType.RELEASE);
        transaction.setAmount(amount);
        transaction.setDescription(description != null ? description : "Giải phóng số dư");
        transaction.setCreatedAt(LocalDateTime.now());
        shipperTransactionRepository.save(transaction);

        return shipperBalanceMapper.toResponse(savedBalance);
    }

    @Transactional
    private ShipperBalanceResponse earnFromOrder(Long userId, Long orderId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền thu nhập phải lớn hơn 0");
        }

        ShipperBalance balance = getOrCreateBalance(userId);
        balance.addBalance(amount);
        ShipperBalance savedBalance = shipperBalanceRepository.save(balance);

        // Create transaction record
        ShipperTransaction transaction = new ShipperTransaction();
        transaction.setShipperId(userId);
        transaction.setRelatedOrderId(orderId);
        transaction.setTransactionType(ShipperTransaction.TransactionType.EARN);
        transaction.setAmount(amount);
        transaction.setDescription("Thu nhập từ đơn hàng #" + orderId);
        transaction.setCreatedAt(LocalDateTime.now());
        shipperTransactionRepository.save(transaction);

        return shipperBalanceMapper.toResponse(savedBalance);
    }

    @Transactional(readOnly = true)
    private List<ShipperTransactionResponse> getTransactionHistory(Long userId) {
        List<ShipperTransaction> transactions = shipperTransactionRepository.findByShipperIdOrderByCreatedAtDesc(userId);
        return transactions.stream()
                .map(shipperTransactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    // === UserId-based methods (for external API) ===
    @Override
    @Transactional(readOnly = true)
    public ShipperBalanceResponse getBalanceByUserId(Long userId) {
        ShipperBalance balance = shipperBalanceRepository.findByShipperId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy số dư của shipper với userId: " + userId));
        return shipperBalanceMapper.toResponse(balance);
    }

    @Override
    @Transactional
    public ShipperBalanceResponse createBalanceForUserId(Long userId) {
        return createBalanceForUser(userId);
    }

    @Override
    @Transactional
    public ShipperBalanceResponse depositBalanceByUserId(Long userId, BalanceTransactionRequest request) {
        return depositBalance(userId, request);
    }

    @Override
    @Transactional
    public ShipperBalanceResponse withdrawBalanceByUserId(Long userId, BalanceTransactionRequest request) {
        return withdrawBalance(userId, request);
    }

    @Override
    @Transactional
    public ShipperBalanceResponse holdBalanceByUserId(Long userId, BigDecimal amount, String description) {
        return holdBalance(userId, amount, description);
    }

    @Override
    @Transactional
    public ShipperBalanceResponse releaseBalanceByUserId(Long userId, BigDecimal amount, String description) {
        return releaseBalance(userId, amount, description);
    }

    @Override
    @Transactional
    public ShipperBalanceResponse earnFromOrderByUserId(Long userId, Long orderId, BigDecimal amount) {
        return earnFromOrder(userId, orderId, amount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipperTransactionResponse> getTransactionHistoryByUserId(Long userId) {
        return getTransactionHistory(userId);
    }

    // === Helper methods ===
    private ShipperBalance getOrCreateBalance(Long userId) {
        return shipperBalanceRepository.findByShipperId(userId)
                .orElseGet(() -> {
                    ShipperBalance newBalance = new ShipperBalance();
                    newBalance.setShipperId(userId);
                    newBalance.setBalance(BigDecimal.ZERO);
                    newBalance.setHoldingBalance(BigDecimal.ZERO);
                    newBalance.setUpdatedAt(LocalDateTime.now());
                    return shipperBalanceRepository.save(newBalance);
                });
    }
}
