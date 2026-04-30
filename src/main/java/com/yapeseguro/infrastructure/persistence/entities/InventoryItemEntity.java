package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_items", schema = "yape")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_profile_id", nullable = false)
    private BusinessProfileEntity businessProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qr_code_id")
    private QrCodeEntity qrCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "product_category", length = 100)
    private String productCategory;

    @Column(length = 50)
    private String sku;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 5;

    @Column(name = "total_units_sold", nullable = false)
    private Integer totalUnitsSold = 0;

    @Column(name = "qr_enabled", nullable = false)
    private boolean qrEnabled = false;

    @Column(name = "sold_this_month", nullable = false)
    private Integer soldThisMonth = 0;

    @Column(name = "sold_this_week", nullable = false)
    private Integer soldThisWeek = 0;

    @Column(name = "revenue_this_month", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenueThisMonth = BigDecimal.ZERO;

    @Column(name = "revenue_this_week", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenueThisWeek = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_sold_at")
    private OffsetDateTime lastSoldAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}