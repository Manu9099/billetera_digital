package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

    private UUID id;

    private UUID walletFromId;
    private UUID walletToId;

    private UUID senderUserId;
    private UUID recipientUserId;

    private String senderName;
    private String recipientName;

    private BigDecimal amount;
    private String currency;

    private String type;
    private String status;
    private String marketplaceStatus;

    private UUID marketplaceDisputeId;

    private String description;
    private String concept;
    private String reference;
    private String notes;

    private OffsetDateTime holdExpiresAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}