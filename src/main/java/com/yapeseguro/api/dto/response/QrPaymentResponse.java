package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class QrPaymentResponse {

    private UUID transactionId;
    private String reference;

    private UUID qrCodeId;
    private String qrType;

    private UUID buyerUserId;
    private String buyerName;
    private UUID sourceWalletId;

    private UUID businessProfileId;
    private String businessName;
    private String businessRuc;
    private UUID businessWalletId;

    private UUID inventoryItemId;
    private String productName;

    private BigDecimal amount;
    private String currency;

    private String status;
    private String description;
    private String notes;

    private Integer remainingStock;

    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
}