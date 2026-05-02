package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AddGroupMemberRequest {

    @NotNull
    private UUID userId;

    @Size(max = 255)
    private String userName;

    @DecimalMin("0.01")
    private BigDecimal amountToPay;
}