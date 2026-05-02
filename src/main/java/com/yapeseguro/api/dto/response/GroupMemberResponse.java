package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class GroupMemberResponse {

    private UUID id;
    private UUID userId;

    private String userName;

    private BigDecimal amountToPay;
    private BigDecimal amountPaid;
    private BigDecimal remainingAmount;

    private String status;

    private OffsetDateTime paidAt;
    private OffsetDateTime addedAt;
    private OffsetDateTime updatedAt;
}