package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets", schema = "yape")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "wallet_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private WalletType walletType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "hold_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal holdAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "PEN";

    @Column(name = "monthly_revenue", precision = 14, scale = 2)
    private BigDecimal monthlyRevenue = BigDecimal.ZERO;

    @Column(name = "monthly_expenses", precision = 14, scale = 2)
    private BigDecimal monthlyExpenses = BigDecimal.ZERO;

    @Column(name = "daily_tx_count")
    private int dailyTxCount = 0;

    @Column(name = "monthly_reset_date")
    private OffsetDateTime monthlyResetDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_transaction_at")
    private OffsetDateTime lastTransactionAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum WalletType { PERSONAL, BUSINESS }
}
