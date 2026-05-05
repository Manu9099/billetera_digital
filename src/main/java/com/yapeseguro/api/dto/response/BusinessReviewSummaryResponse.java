package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BusinessReviewSummaryResponse {

    private UUID businessProfileId;

    private String businessName;

    private BigDecimal averageRating;

    private Integer totalReviews;
}
