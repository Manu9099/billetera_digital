package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResolveDisputeRequest {

    @NotBlank
    private String resolution;

    private BigDecimal refundAmount;

    private String resolutionNotes;
}