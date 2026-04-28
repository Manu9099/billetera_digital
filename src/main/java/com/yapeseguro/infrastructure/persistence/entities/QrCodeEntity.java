package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name = "qr_codes", schema = "yape")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class QrCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_user_id", nullable = false)
    private UserEntity creatorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_wallet_id", nullable = false)
    private WalletEntity creatorWallet;

    @Column(name = "qr_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private QrType qrType = QrType.PAYMENT;

    @Column(name = "qr_data", nullable = false, columnDefinition = "TEXT")
    private String qrData;

    @Column(name = "qr_image_url", columnDefinition = "TEXT")
    private String qrImageUrl;

    @Column(length = 255)
    private String description;

    @Column(name = "fixed_amount", precision = 14, scale = 2)
    private BigDecimal fixedAmount;

    @Column(nullable = false, length = 3)
    private String currency = "PEN";

    @Column(name = "scans_count", nullable = false)
    private int scansCount = 0;

    @Column(name = "payments_count", nullable = false)
    private int paymentsCount = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum QrType { PAYMENT, FIXED_AMOUNT, INVENTORY }
}