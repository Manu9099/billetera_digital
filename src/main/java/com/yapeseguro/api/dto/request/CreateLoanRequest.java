package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CreateLoanRequest {

    @NotNull
    private UUID borrowerUserId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal originalAmount;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal interestRate = BigDecimal.ZERO;

    @DecimalMin("0.00")
    private BigDecimal lateFeePerDay = BigDecimal.ZERO;

    private OffsetDateTime dueDate;

    @Size(max = 2000)
    private String notes;
}
