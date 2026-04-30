package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "business_profiles", schema = "yape")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_wallet_id", nullable = false)
    private WalletEntity businessWallet;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "business_category", length = 100)
    private String businessCategory;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String address;

    private Double latitude;

    private Double longitude;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(name = "business_phone_number", length = 20)
    private String businessPhoneNumber;

    @Column(name = "business_email", length = 255)
    private String businessEmail;

    @Column(length = 255)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verification_doc_url", columnDefinition = "TEXT")
    private String verificationDocUrl;

    @Column(name = "verification_date")
    private OffsetDateTime verificationDate;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews = 0;

    @Column(name = "total_transactions", nullable = false)
    private Integer totalTransactions = 0;

    @Column(name = "total_revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "auto_confirm_receipts", nullable = false)
    private boolean autoConfirmReceipts = false;

    @Column(name = "show_frequent_customers", nullable = false)
    private boolean showFrequentCustomers = false;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        REJECTED,
        SUSPENDED
    }
}