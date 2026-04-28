package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddInventoryItemRequest {

    @NotBlank
    @Size(max = 255)
    private String productName;

    private String description;

    private String productCategory;

    private String sku;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer currentStock;

    @Min(0)
    private Integer lowStockThreshold = 5;

    private boolean qrEnabled = false;
}
