package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ExpenseCategoryResponse {

    private UUID id;
    private String categoryName;
    private String iconCode;
    private String colorHex;
    private OffsetDateTime createdAt;
}