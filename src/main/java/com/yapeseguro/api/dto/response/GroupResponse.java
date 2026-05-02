package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GroupResponse {

    private UUID id;

    private UUID creatorUserId;
    private String creatorName;

    private String groupName;
    private String description;
    private String groupType;

    private BigDecimal totalAmount;
    private BigDecimal currentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal perPersonAmount;
    private BigDecimal progressPercentage;

    private String currency;

    private Integer memberCount;
    private Integer paidMemberCount;
    private Integer pendingMemberCount;

    private String status;

    private OffsetDateTime targetDate;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<GroupMemberResponse> members;
}