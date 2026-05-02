package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class LoanRepaymentResponse {

    private UUID loanId;

    private UUID transactionId;
    private String transactionReference;

    private BigDecimal amountPaid;
    private BigDecimal remainingBalance;
    private BigDecimal currentAmountDue;

    private String loanStatus;
    private OffsetDateTime completedDate;
}