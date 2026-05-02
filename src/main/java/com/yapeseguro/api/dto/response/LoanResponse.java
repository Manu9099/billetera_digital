package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class LoanResponse {

    private UUID id;

    private UUID borrowerUserId;
    private String borrowerName;

    private UUID lenderUserId;
    private String lenderName;

    private UUID transactionId;

    private BigDecimal originalAmount;
    private BigDecimal remainingBalance;
    private BigDecimal currentDebt;

    private BigDecimal interestRate;
    private BigDecimal totalAmountToReturn;
    private BigDecimal lateFeePerDay;

    private String loanStatus;

    private OffsetDateTime loanDate;
    private OffsetDateTime dueDate;
    private OffsetDateTime completedDate;

    private Boolean overdue;

    private String notes;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}