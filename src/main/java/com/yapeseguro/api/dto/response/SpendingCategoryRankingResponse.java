package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class SpendingCategoryRankingResponse {

    private Integer rank;

    private UUID categoryId;
    private String categoryName;
    private String iconCode;
    private String colorHex;

    private BigDecimal totalSpent;
    private Integer transactionCount;
    private BigDecimal percentage;
}