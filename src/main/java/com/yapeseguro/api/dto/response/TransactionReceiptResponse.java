package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionReceiptResponse {

    private UUID transactionId;
    private String reference;

    private UUID senderUserId;
    private UUID recipientUserId;

    private String senderName;
    private String recipientName;

    private UUID walletFromId;
    private UUID walletToId;

    private BigDecimal amount;
    private String currency;

    private String type;
    private String status;
    private String marketplaceStatus;

    private String concept;
    private String description;
    private String notes;

    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
}