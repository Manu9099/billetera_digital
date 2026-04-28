package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CreateScheduledPaymentRequest {

    @NotBlank
    @Size(max = 255)
    private String recipientName;

    @Pattern(regexp = "^9\\d{8}$")
    private String recipientPhone;

    private UUID recipientUserId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 100)
    private String concept;

    @NotBlank
    private String frequency; // DAILY, WEEKLY, BIWEEKLY, MONTHLY

    @Min(1)
    @Max(28)
    private Integer dayOfMonth;

    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @NotNull
    private OffsetDateTime nextPaymentDate;

    private OffsetDateTime endDate;

    private boolean autoPayEnabled = false;

    @Min(1)
    @Max(30)
    private int notifyDaysInAdvance = 1;
}