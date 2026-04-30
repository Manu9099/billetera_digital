package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class QrPaymentRequest {

    @NotNull(message = "El QR es obligatorio")
    private UUID qrCodeId;

    /**
     * Para QR PAYMENT libre, amount es obligatorio.
     * Para FIXED_AMOUNT o INVENTORY, el monto sale del QR/producto.
     */
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal amount;

    @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
    private String description;

    @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
    private String notes;
}