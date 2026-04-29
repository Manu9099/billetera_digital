package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;



@Entity
@Table(name = "transactions", schema = "yape")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_from_id", nullable = false)
    private WalletEntity walletFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_to_id", nullable = false)
    private WalletEntity walletTo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "PEN";

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TxType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TxStatus status = TxStatus.PENDING;

    @Column(length = 255)
    private String description;

    @Column(length = 100)
    private String concept;

    @Column(nullable = false, length = 60, unique = true)
    private String reference;

    // Feature #1: Marketplace Protection
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marketplace_dispute_id")
    private DisputeEntity marketplaceDispute;

    @Column(name = "marketplace_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private MpStatus marketplaceStatus = MpStatus.NORMAL;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    // Feature #9: QR monto fijo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qr_code_id")
    private QrCodeEntity qrCode;

    @Column(name = "qr_description", length = 255)
    private String qrDescription;

    @Column(name = "qr_fixed_amount", precision = 14, scale = 2)
    private BigDecimal qrFixedAmount;

    @Column(name = "scheduled_payment_id")
    private UUID scheduledPaymentId;

    private String notes;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum TxType     { P2P, QR_PAYMENT, SCHEDULED, MARKETPLACE }
    public enum TxStatus   { PENDING, COMPLETED, FAILED, HELD, RELEASED, CANCELLED }
    public enum MpStatus   { NORMAL, HELD, BUYER_CONFIRMED, DISPUTED }
}

