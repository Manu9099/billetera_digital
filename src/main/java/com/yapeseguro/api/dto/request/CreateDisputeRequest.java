package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateDisputeRequest {

    @NotNull
    private UUID transactionId;

    @NotBlank
    private String reason;

    @NotBlank
    @Size(max = 2000)
    private String description;

    private BigDecimal disputedAmount;

    private String recipientPhone;

    private String qrPhotoUrl;

    private String chatTranscript;
}
