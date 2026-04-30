package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopUpRequest {

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal amount;

    @NotBlank(message = "La moneda es obligatoria")
    @Size(min = 3, max = 3, message = "La moneda debe tener 3 caracteres")
    private String currency;

    @Size(max = 50, message = "El método de pago no puede exceder 50 caracteres")
    private String paymentMethod;

    @Size(max = 100, message = "La referencia externa no puede exceder 100 caracteres")
    private String externalReference;

    @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
    private String description;
}