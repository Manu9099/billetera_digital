package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecurityAnalyticsResponse {

    private String yearMonth;
    private Long totalTransactions;
    private Long disputedTransactions;
    private String riskLevel;
    private String message;
}