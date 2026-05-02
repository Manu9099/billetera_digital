package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ScheduledPaymentResponse {

    private UUID id;

    private UUID walletFromId;
    private UUID walletToId;
    private UUID recipientUserId;

    private String recipientName;
    private String recipientPhone;

    private BigDecimal amount;
    private String currency;

    private String concept;
    private String description;

    private String frequency;
    private Integer dayOfMonth;
    private Integer dayOfWeek;

    private OffsetDateTime nextPaymentDate;
    private OffsetDateTime lastPaymentDate;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;

    private Boolean autoPayEnabled;
    private Integer failureRetryCount;
    private Integer timesExecuted;
    private Integer notifyDaysInAdvance;

    private String status;
    private OffsetDateTime pausedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}