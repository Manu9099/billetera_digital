package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class QrPaymentPreviewResponse {

    private UUID qrCodeId;
    private String qrType;

    private UUID businessProfileId;
    private UUID businessWalletId;
    private String businessName;
    private String businessRuc;
    private String businessCategory;

    private UUID inventoryItemId;
    private String productName;
    private String productCategory;
    private String imageUrl;
    private Integer currentStock;

    private BigDecimal amount;
    private String currency;
    private String description;

    private Boolean fixedAmount;
    private Boolean inventoryPayment;
    private Boolean available;
}