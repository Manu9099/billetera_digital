package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class QrPaymentRequest {

    @NotNull
    private UUID qrCodeId;

    @DecimalMin("0.01")
    private BigDecimal amount;

    @Size(max = 255)
    private String description;

    private String notes;
}