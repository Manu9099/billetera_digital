package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class P2PRequest {

    @NotNull
    private UUID sourceWalletId;

    @NotNull
    private UUID recipientUserId;

    @NotNull
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 100)
    private String concept;

    @Size(max = 500)
    private String notes;
}