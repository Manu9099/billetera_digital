package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RepayLoanRequest {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @Size(max = 500)
    private String notes;
}