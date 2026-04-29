package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {

    private UUID id;
    private String walletType;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal holdAmount;
    private String currency;
    private BigDecimal monthlyRevenue;
    private BigDecimal monthlyExpenses;
    private Integer dailyTxCount;
    private Boolean active;
}