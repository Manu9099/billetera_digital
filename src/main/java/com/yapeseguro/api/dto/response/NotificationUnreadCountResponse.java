package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationUnreadCountResponse {

    private long unreadCount;
}