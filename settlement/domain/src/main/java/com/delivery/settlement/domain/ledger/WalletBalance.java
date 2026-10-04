package com.delivery.settlement.domain.ledger;

import java.math.BigDecimal;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Direction.*;
import static com.delivery.settlement.domain.ledger.LedgerPosting.Wallet.*;

/** Immutable dual-wallet projection; implements the existing accounting policy. */
public record WalletBalance(BigDecimal available, BigDecimal pending, BigDecimal holding,
        BigDecimal deposit, BigDecimal reservedDeposit, BigDecimal totalDeposited, BigDecimal totalCodCollected) {
    public static WalletBalance zero() {
        return new WalletBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public WalletBalance apply(LedgerPosting posting) {
        BigDecimal amount = posting.amount();
        switch (posting.reason()) {
            case WITHDRAW, DEPOSIT_TOPUP: return this;
            case HOLD: return copy(available.subtract(amount), pending, holding.add(amount), deposit, totalDeposited, totalCodCollected);
            case RELEASE: return copy(available.add(amount), pending, holding.subtract(amount), deposit, totalDeposited, totalCodCollected);
            case COD_SETTLEMENT:
                if (posting.direction() == DEBIT) {
                    if (deposit.compareTo(amount) < 0) {
                        throw new InsufficientWalletFunds(String.format("Insufficient COD deposit. Available: %s, Required: %s", deposit, amount));
                    }
                    return copy(available, pending, holding, deposit.subtract(amount), totalDeposited, totalCodCollected.add(amount));
                }
                return this;
            default:
                BigDecimal delta = posting.direction() == CREDIT ? amount : amount.negate();
                return posting.wallet() == DEPOSIT
                        ? copy(available, pending, holding, deposit.add(delta), totalDeposited, totalCodCollected)
                        : copy(available.add(delta), pending, holding, deposit, totalDeposited, totalCodCollected);
        }
    }

    public WalletBalance topUp(BigDecimal amount) {
        return copy(available, pending, holding, deposit.add(amount), totalDeposited.add(amount), totalCodCollected);
    }

    public WalletBalance reserveWithdrawal(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw new InsufficientWalletFunds(String.format("Insufficient earnings balance. Available: %s, Requested: %s", available, amount));
        }
        return copy(available.subtract(amount), pending.add(amount), holding, deposit, totalDeposited, totalCodCollected);
    }

    public WalletBalance approveWithdrawal(BigDecimal amount) {
        return copy(available, pending.subtract(amount), holding, deposit, totalDeposited, totalCodCollected);
    }

    public WalletBalance rejectWithdrawal(BigDecimal amount) {
        return copy(available.add(amount), pending.subtract(amount), holding, deposit, totalDeposited, totalCodCollected);
    }

    public void requireReleaseCapacity(BigDecimal amount) {
        if (holding.compareTo(amount) < 0) {
            throw new InsufficientWalletFunds(String.format("Insufficient holding balance. Holding: %s, Requested: %s", holding, amount));
        }
    }

    public boolean canCoverCod(BigDecimal amount) {
        return deposit.subtract(reservedDeposit == null ? BigDecimal.ZERO : reservedDeposit).compareTo(amount) >= 0;
    }

    public BigDecimal reservedCodCapacity() { return reservedDeposit == null ? BigDecimal.ZERO : reservedDeposit; }

    public WalletBalance reserveCodCapacity(BigDecimal requested) {
        if (deposit.subtract(reservedCodCapacity()).compareTo(requested) < 0) {
            throw new InsufficientWalletFunds("Insufficient COD capacity for batch hold");
        }
        return withReservedCodCapacity(reservedCodCapacity().add(requested));
    }

    public WalletBalance releaseCodCapacity(BigDecimal amount) {
        return withReservedCodCapacity(reservedCodCapacity().subtract(amount).max(BigDecimal.ZERO));
    }

    private WalletBalance withReservedCodCapacity(BigDecimal reserved) {
        return new WalletBalance(available, pending, holding, deposit, reserved, totalDeposited, totalCodCollected);
    }

    private WalletBalance copy(BigDecimal available, BigDecimal pending, BigDecimal holding,
            BigDecimal deposit, BigDecimal deposited, BigDecimal collected) {
        return new WalletBalance(available, pending, holding, deposit, reservedDeposit, deposited, collected);
    }
}
