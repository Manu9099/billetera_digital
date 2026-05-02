package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ReceiptResponse {

    private UUID id;
    private UUID transactionId;

    private String receiptNumber;

    private String businessName;
    private String businessRuc;
    private String customerName;

    private BigDecimal amount;
    private String currency;

    private String concept;
    private String description;

    private String transactionType;
    private String transactionStatus;
    private String marketplaceStatus;
    private String transactionReference;

    private String receiptHtml;
    private String receiptPdfUrl;
    private String qrCodeUrl;

    private Integer printedCount;
    private String emailedTo;
    private Boolean sentWhatsapp;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}