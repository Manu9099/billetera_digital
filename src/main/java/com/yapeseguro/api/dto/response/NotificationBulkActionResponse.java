package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationBulkActionResponse {

    private int affected;
    private String message;
}