package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class InventoryItemResponse {

    private UUID id;
    private UUID businessProfileId;
    private UUID qrCodeId;

    private String productName;
    private String description;
    private String productCategory;
    private String sku;
    private String imageUrl;

    private BigDecimal price;
    private Integer currentStock;
    private Integer lowStockThreshold;
    private Boolean lowStock;

    private Integer totalUnitsSold;
    private Boolean qrEnabled;

    private Integer soldThisMonth;
    private Integer soldThisWeek;
    private BigDecimal revenueThisMonth;
    private BigDecimal revenueThisWeek;

    private Boolean active;
    private OffsetDateTime lastSoldAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}