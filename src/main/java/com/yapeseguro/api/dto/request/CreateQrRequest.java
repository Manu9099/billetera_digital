package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateQrRequest {

    @Size(max = 255)
    private String description;

    @DecimalMin("0.01")
    private BigDecimal fixedAmount;

    private String currency = "PEN";

    private String qrType = "PAYMENT";
}