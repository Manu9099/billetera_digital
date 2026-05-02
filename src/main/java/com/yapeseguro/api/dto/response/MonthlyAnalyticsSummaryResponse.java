package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MonthlyAnalyticsSummaryResponse {

    private UUID walletId;
    private String walletType;
    private String yearMonth;

    private BigDecimal totalSpent;
    private Integer transactionCount;

    private String topCategoryName;
    private BigDecimal topCategoryAmount;

    private List<SpendingCategoryRankingResponse> ranking;
}