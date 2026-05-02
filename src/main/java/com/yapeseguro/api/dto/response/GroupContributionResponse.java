package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class GroupContributionResponse {

    private UUID groupId;
    private UUID memberId;
    private UUID transactionId;
    private String transactionReference;

    private BigDecimal amountPaid;
    private BigDecimal memberTotalPaid;
    private BigDecimal groupCurrentAmount;
    private BigDecimal groupRemainingAmount;

    private String memberStatus;
    private String groupStatus;
}