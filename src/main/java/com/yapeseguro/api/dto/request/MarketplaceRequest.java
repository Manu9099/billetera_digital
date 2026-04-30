package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class MarketplaceRequest {

    @NotNull(message = "El vendedor es obligatorio")
    private UUID sellerUserId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal amount;

    @NotBlank(message = "La descripción del producto es obligatoria")
    @Size(max = 255, message = "La descripción del producto no puede exceder 255 caracteres")
    private String productDescription;

    @Min(value = 1, message = "La retención mínima es de 1 día")
    @Max(value = 30, message = "La retención máxima es de 30 días")
    private int holdDays = 7;

    @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
    private String notes;
}