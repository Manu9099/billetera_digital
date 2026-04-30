package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BusinessProfileResponse {

    private UUID id;

    private UUID ownerUserId;
    private UUID businessWalletId;

    private String businessName;
    private String ruc;
    private String businessCategory;
    private String description;

    private String address;
    private Double latitude;
    private Double longitude;
    private String city;
    private String district;

    private String businessPhoneNumber;
    private String businessEmail;
    private String website;

    private String verificationStatus;
    private OffsetDateTime verificationDate;

    private BigDecimal averageRating;
    private Integer totalReviews;
    private Integer totalTransactions;
    private BigDecimal totalRevenue;

    private Boolean autoConfirmReceipts;
    private Boolean showFrequentCustomers;
    private Boolean active;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}