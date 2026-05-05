package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.AddDisputeTimelineNoteRequest;
import com.yapeseguro.api.dto.response.DisputeTimelineEventResponse;
import com.yapeseguro.infrastructure.persistence.entities.DisputeEntity;
import com.yapeseguro.infrastructure.persistence.entities.DisputeTimelineEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.repositories.DisputeRepository;
import com.yapeseguro.infrastructure.persistence.repositories.DisputeTimelineRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisputeTimelineService {

    private final UserRepository userRepository;
    private final DisputeRepository disputeRepository;
    private final DisputeTimelineRepository timelineRepository;

    @Transactional(readOnly = true)
    public List<DisputeTimelineEventResponse> getTimeline(
            UUID disputeId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        DisputeEntity dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Disputa no encontrada"));

        validateDisputeBelongsToUser(dispute, user);

        return timelineRepository.findByDisputeIdOrderByCreatedAtAsc(disputeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DisputeTimelineEventResponse addNote(
            UUID disputeId,
            AddDisputeTimelineNoteRequest request,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        DisputeEntity dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Disputa no encontrada"));

        validateDisputeBelongsToUser(dispute, user);

        DisputeTimelineEntity event = createEvent(
                dispute,
                user,
                resolveActorRole(dispute, user),
                DisputeTimelineEntity.EventType.NOTE_ADDED,
                "Nota agregada",
                normalizeRequired(request.getMessage(), "El mensaje es obligatorio"),
                null
        );

        return toResponse(event);
    }

    @Transactional
    public void recordOpened(
            DisputeEntity dispute,
            UserEntity actor
    ) {
        createEvent(
                dispute,
                actor,
                resolveActorRole(dispute, actor),
                DisputeTimelineEntity.EventType.DISPUTE_OPENED,
                "Disputa abierta",
                "El comprador abrió una disputa sobre esta operación.",
                buildMetadata("""
                        {"status":"%s","reason":"%s","amount":"%s"}
                        """.formatted(
                        dispute.getStatus().name(),
                        dispute.getReason().name(),
                        dispute.getDisputedAmount()
                ))
        );
    }

    @Transactional
    public void recordResolved(
            DisputeEntity dispute,
            UserEntity actor,
            DisputeEntity.DisputeResolution resolution,
            BigDecimal refundAmount
    ) {
        DisputeTimelineEntity.EventType eventType = switch (resolution) {
            case REFUND -> DisputeTimelineEntity.EventType.RESOLVED_REFUND;
            case PARTIAL_REFUND -> DisputeTimelineEntity.EventType.RESOLVED_PARTIAL_REFUND;
            case DISMISSED -> DisputeTimelineEntity.EventType.RESOLVED_DISMISSED;
        };

        String title = switch (resolution) {
            case REFUND -> "Disputa resuelta con reembolso total";
            case PARTIAL_REFUND -> "Disputa resuelta con reembolso parcial";
            case DISMISSED -> "Disputa desestimada";
        };

        String message = switch (resolution) {
            case REFUND -> "Se resolvió la disputa devolviendo el monto completo al comprador.";
            case PARTIAL_REFUND -> "Se resolvió la disputa con devolución parcial al comprador.";
            case DISMISSED -> "Se resolvió la disputa liberando el pago al vendedor.";
        };

        createEvent(
                dispute,
                actor,
                resolveActorRole(dispute, actor),
                eventType,
                title,
                message,
                buildMetadata("""
                        {"resolution":"%s","refundAmount":"%s","status":"%s"}
                        """.formatted(
                        resolution.name(),
                        refundAmount != null ? refundAmount : BigDecimal.ZERO,
                        dispute.getStatus().name()
                ))
        );
    }

    @Transactional
    public void recordAutoResolved(DisputeEntity dispute) {
        createEvent(
                dispute,
                null,
                DisputeTimelineEntity.ActorRole.SYSTEM,
                DisputeTimelineEntity.EventType.AUTO_RESOLVED,
                "Resolución automática",
                "La disputa venció sin resolución manual y el sistema aplicó la resolución automática.",
                buildMetadata("""
                        {"resolution":"%s","refundAmount":"%s","status":"%s"}
                        """.formatted(
                        dispute.getResolution() != null ? dispute.getResolution().name() : null,
                        dispute.getRefundAmount() != null ? dispute.getRefundAmount() : BigDecimal.ZERO,
                        dispute.getStatus().name()
                ))
        );
    }

    @Transactional
    public void recordStatusChanged(
            DisputeEntity dispute,
            UserEntity actor,
            DisputeEntity.DisputeStatus previousStatus,
            DisputeEntity.DisputeStatus newStatus
    ) {
        createEvent(
                dispute,
                actor,
                actor != null
                        ? resolveActorRole(dispute, actor)
                        : DisputeTimelineEntity.ActorRole.SYSTEM,
                DisputeTimelineEntity.EventType.STATUS_CHANGED,
                "Estado actualizado",
                "La disputa cambió de estado.",
                buildMetadata("""
                        {"previousStatus":"%s","newStatus":"%s"}
                        """.formatted(
                        previousStatus.name(),
                        newStatus.name()
                ))
        );
    }

    private DisputeTimelineEntity createEvent(
            DisputeEntity dispute,
            UserEntity actor,
            DisputeTimelineEntity.ActorRole actorRole,
            DisputeTimelineEntity.EventType eventType,
            String title,
            String message,
            String metadataJson
    ) {
        DisputeTimelineEntity event = DisputeTimelineEntity.builder()
                .dispute(dispute)
                .transaction(dispute.getTransaction())
                .actorUser(actor)
                .actorRole(actorRole)
                .eventType(eventType)
                .title(normalizeRequired(title, "El título del evento es obligatorio"))
                .message(normalizeRequired(message, "El mensaje del evento es obligatorio"))
                .metadataJson(normalize(metadataJson))
                .build();

        return timelineRepository.save(event);
    }

    private void validateDisputeBelongsToUser(
            DisputeEntity dispute,
            UserEntity user
    ) {
        UUID userId = user.getId();

        boolean isCreator = dispute.getCreatedByUser().getId().equals(userId);
        boolean isRespondent = dispute.getRespondentUser().getId().equals(userId);

        if (!isCreator && !isRespondent) {
            throw new IllegalArgumentException("No tienes permiso para ver esta disputa");
        }
    }

    private DisputeTimelineEntity.ActorRole resolveActorRole(
            DisputeEntity dispute,
            UserEntity actor
    ) {
        if (actor == null) {
            return DisputeTimelineEntity.ActorRole.SYSTEM;
        }

        if (dispute.getCreatedByUser().getId().equals(actor.getId())) {
            return DisputeTimelineEntity.ActorRole.BUYER;
        }

        if (dispute.getRespondentUser().getId().equals(actor.getId())) {
            return DisputeTimelineEntity.ActorRole.SELLER;
        }

        return DisputeTimelineEntity.ActorRole.ADMIN;
    }

    private DisputeTimelineEventResponse toResponse(DisputeTimelineEntity event) {
        UserEntity actor = event.getActorUser();

        return DisputeTimelineEventResponse.builder()
                .id(event.getId())
                .disputeId(event.getDispute().getId())
                .transactionId(event.getTransaction().getId())
                .actorUserId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? fullName(actor) : "Sistema")
                .actorRole(event.getActorRole().name())
                .eventType(event.getEventType().name())
                .title(event.getTitle())
                .message(event.getMessage())
                .metadataJson(event.getMetadataJson())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private String fullName(UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private String buildMetadata(String value) {
        return normalize(value);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);

        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }

        return normalized;
    }
}