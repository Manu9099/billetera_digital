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

    private BigDecimal amount;
    private String currency;

    private String type;
    private String status;
    private String marketplaceStatus;

    private String description;
    private String concept;
    private String reference;
    private String notes;

    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
}
