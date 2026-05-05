package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DisputeTimelineEventResponse {

    private UUID id;

    private UUID disputeId;

    private UUID transactionId;

    private UUID actorUserId;

    private String actorName;

    private String actorRole;

    private String eventType;

    private String title;

    private String message;

    private String metadataJson;

    private OffsetDateTime createdAt;
}