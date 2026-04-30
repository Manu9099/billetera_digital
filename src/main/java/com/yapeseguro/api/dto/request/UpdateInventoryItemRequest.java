package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateInventoryItemRequest {

    @Size(max = 255)
    private String productName;

    @Size(max = 2000)
    private String description;

    @Size(max = 100)
    private String productCategory;

    @Size(max = 50)
    private String sku;

    @Size(max = 2000)
    private String imageUrl;

    @DecimalMin("0.01")
    private BigDecimal price;

    @Min(0)
    private Integer currentStock;

    @Min(0)
    private Integer lowStockThreshold;

    private Boolean qrEnabled;

    private Boolean active;
}