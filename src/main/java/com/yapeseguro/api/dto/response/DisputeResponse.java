package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DisputeResponse {

    private UUID id;
    private UUID transactionId;

    private UUID createdByUserId;
    private String createdByName;

    private UUID respondentUserId;
    private String respondentName;

    private String reason;
    private String description;
    private BigDecimal disputedAmount;

    private String status;
    private Boolean marketplaceDispute;

    private String recipientPhone;
    private String qrPhotoUrl;
    private String chatTranscript;

    private OffsetDateTime openedAt;
    private OffsetDateTime evidenceSubmittedAt;
    private OffsetDateTime inResolutionAt;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime expiresAt;

    private String resolution;
    private BigDecimal refundAmount;
    private String resolutionNotes;

    private String transactionStatus;
    private String transactionMarketplaceStatus;
    private String transactionReference;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}