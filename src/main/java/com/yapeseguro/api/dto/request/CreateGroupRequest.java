package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class CreateGroupRequest {

    @NotBlank
    @Size(max = 255)
    private String groupName;

    private String description;

    @NotBlank
    private String groupType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal totalAmount;

    private BigDecimal perPersonAmount;

    private OffsetDateTime targetDate;
}