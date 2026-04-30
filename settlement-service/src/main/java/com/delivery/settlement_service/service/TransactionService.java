package com.delivery.settlement_service.service;

import com.delivery.settlement_service.dto.request.RejectWithdrawalRequest;
import com.delivery.settlement_service.dto.response.TransactionResponse;
import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionDirection;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.WalletType;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionService {

    /**
     * Create a transaction on the EARNINGS wallet (default)
     */
    Transaction createTransaction(Long entityId, EntityType entityType, Long orderId,
                                 TransactionDirection direction, TransactionReason reason,
                                 BigDecimal amount, String description);

    /**
     * ✅ Create a transaction specifying which wallet (EARNINGS or DEPOSIT)
     */
    Transaction createTransaction(Long entityId, EntityType entityType, Long orderId,
                                 TransactionDirection direction, TransactionReason reason,
                                 BigDecimal amount, String description, WalletType walletType);

    /**
     * Helper: Earn from order (creates CREDIT transaction with ORDER_EARNING/DELIVERY_FEE reason)
     */
    Transaction earnFromOrder(Long entityId, EntityType entityType, Long orderId,
                             BigDecimal amount, String description);

    /**
     * Request withdrawal from EARNINGS wallet (creates PENDING DEBIT transaction)
     */
    Transaction requestWithdrawal(Long entityId, EntityType entityType,
                                 BigDecimal amount, Long requestedBy);

    /**
     * Approve withdrawal (change status PENDING → COMPLETED, update balance)
     */
    Transaction approveWithdrawal(Long transactionId, Long adminId);

    /**
     * Reject withdrawal (change status PENDING → FAILED, restore balance)
     */
    Transaction rejectWithdrawal(Long transactionId, Long adminId, RejectWithdrawalRequest request);

    /**
     * Reverse a transaction (create opposite transaction)
     */
    Transaction reverseTransaction(Long transactionId, Long adminId, String reason);

    /**
     * Hold balance (for shippers - creates DEBIT transaction with HOLD reason)
     */
    Transaction holdBalance(Long entityId, BigDecimal amount, String description);

    /**
     * Release balance (for shippers - creates CREDIT transaction with RELEASE reason)
     */
    Transaction releaseBalance(Long entityId, BigDecimal amount, String description);

    /**
     * ✅ Shipper nạp tiền vào Ví Ký quỹ (Deposit Wallet)
     */
    Transaction topUpDeposit(Long shipperId, BigDecimal amount, String paymentMethod);

    /**
     * ✅ Kiểm tra shipper có đủ ký quỹ để nhận đơn COD không
     * @return true nếu depositBalance >= codAmount
     */
    boolean checkCodEligibility(Long shipperId, BigDecimal codAmount);

    /**
     * Get transaction history for an entity
     */
    List<TransactionResponse> getTransactions(Long entityId, EntityType entityType);

    /**
     * Get transaction by ID
     */
    TransactionResponse getTransactionById(Long transactionId);

    /**
     * Get pending withdrawals (for admin)
     */
    List<TransactionResponse> getPendingWithdrawals();

    /**
     * Get all transactions (for admin)
     */
    List<TransactionResponse> getAllTransactions();
}
