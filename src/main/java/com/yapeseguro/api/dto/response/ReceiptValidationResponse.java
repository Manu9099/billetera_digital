package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class ReceiptValidationResponse {

    private Boolean valid;
    private String receiptNumber;
    private String transactionReference;

    private String businessName;
    private String businessRuc;
    private String customerName;

    private BigDecimal amount;
    private String currency;

    private String transactionType;
    private String transactionStatus;
    private String marketplaceStatus;

    private OffsetDateTime issuedAt;
}