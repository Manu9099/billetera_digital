package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class MarketplacePaymentRequest {

    @NotNull(message = "La billetera origen es obligatoria")
    private UUID sourceWalletId;

    @NotNull(message = "El vendedor es obligatorio")
    private UUID sellerUserId;

    @NotBlank(message = "El ID de la orden es obligatorio")
    @Size(max = 100, message = "El ID de la orden no puede exceder 100 caracteres")
    private String orderId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal amount;

    @NotBlank(message = "La moneda es obligatoria")
    @Size(min = 3, max = 3, message = "La moneda debe tener 3 caracteres")
    private String currency;

    @NotBlank(message = "El concepto es obligatorio")
    @Size(max = 120, message = "El concepto no puede exceder 120 caracteres")
    private String concept;

    @Size(max = 255, message = "Las notas no pueden exceder 255 caracteres")
    private String notes;
}