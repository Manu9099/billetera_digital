package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayGroupContributionRequest {

    /**
     * Si no se envía, se paga el pendiente del miembro.
     */
    @DecimalMin("0.01")
    private BigDecimal amount;

    @Size(max = 500)
    private String notes;
}