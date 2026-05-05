package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {

    private UUID id;

    private String title;

    private String message;

    private String notificationType;

    private UUID relatedEntityId;

    private boolean read;

    private OffsetDateTime readAt;

    private String sentVia;

    private OffsetDateTime createdAt;

    private OffsetDateTime expiresAt;

    private boolean expired;
}