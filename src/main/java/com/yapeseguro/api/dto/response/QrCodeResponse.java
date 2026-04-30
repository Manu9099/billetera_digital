package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class QrCodeResponse {

    private UUID id;

    private UUID creatorUserId;
    private UUID creatorWalletId;
    private UUID businessProfileId;
    private UUID inventoryItemId;

    private String qrType;
    private String qrData;
    private String qrImageUrl;

    private String description;
    private BigDecimal fixedAmount;
    private String currency;

    private Integer scansCount;
    private Integer paymentsCount;
    private BigDecimal revenue;

    private Boolean active;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}