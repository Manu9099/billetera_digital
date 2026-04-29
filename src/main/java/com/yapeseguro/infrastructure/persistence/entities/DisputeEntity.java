package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name = "disputes", schema = "yape")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DisputeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserEntity createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "respondent_user_id", nullable = false)
    private UserEntity respondentUser;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DisputeReason reason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "disputed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal disputedAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "is_marketplace_dispute", nullable = false)
    private boolean isMarketplaceDispute = false;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "qr_photo_url", columnDefinition = "TEXT")
    private String qrPhotoUrl;

    @Column(name = "chat_transcript", columnDefinition = "TEXT")
    private String chatTranscript;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "evidence_submitted_at")
    private OffsetDateTime evidenceSubmittedAt;

    @Column(name = "in_resolution_at")
    private OffsetDateTime inResolutionAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private DisputeResolution resolution;

    @Column(name = "refund_amount", precision = 14, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum DisputeReason      { UNAUTHORIZED_TRANSACTION, FRAUD, WRONG_AMOUNT,
        PRODUCT_NOT_RECEIVED, PRODUCT_DEFECTIVE,
        SERVICE_NOT_PROVIDED, DUPLICATE_CHARGE, OTHER }
    public enum DisputeStatus      { OPEN, EVIDENCE_REVIEW, IN_RESOLUTION, RESOLVED, CLOSED }
    public enum DisputeResolution  { REFUND, PARTIAL_REFUND, DISMISSED }
}
