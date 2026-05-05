package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BusinessReviewResponse {

    private UUID id;

    private UUID businessProfileId;

    private String businessName;

    private UUID transactionId;

    private String transactionReference;

    private UUID customerUserId;

    private String customerName;

    private Integer rating;

    private String comment;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}