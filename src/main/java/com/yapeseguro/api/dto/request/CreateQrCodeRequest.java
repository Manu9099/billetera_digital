package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateQrCodeRequest {

    @Size(max = 255)
    private String description;

    @DecimalMin("0.01")
    private BigDecimal fixedAmount;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid ISO code")
    private String currency;

    @Size(max = 2000)
    private String qrImageUrl;
}