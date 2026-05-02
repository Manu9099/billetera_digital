package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LoanSummaryResponse {

    private Integer activeBorrowedLoans;
    private Integer activeLentLoans;

    private BigDecimal totalBorrowedRemaining;
    private BigDecimal totalLentRemaining;

    private Integer overdueBorrowedLoans;
    private Integer overdueLentLoans;
}