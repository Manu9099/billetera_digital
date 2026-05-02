package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class LoanPreviewResponse {

    private UUID borrowerUserId;
    private String borrowerName;

    private BigDecimal originalAmount;
    private BigDecimal interestRate;
    private BigDecimal interestAmount;
    private BigDecimal totalAmountToReturn;

    private BigDecimal lateFeePerDay;
    private OffsetDateTime dueDate;

    private String transparencySummary;
}