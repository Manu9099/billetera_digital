package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_payments", schema = "yape")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_from_id", nullable = false)
    private WalletEntity walletFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_to_id")
    private WalletEntity walletTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id")
    private UserEntity recipientUser;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "PEN";

    @Column(length = 100)
    private String concept;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;

    @Column(name = "day_of_month")
    private Short dayOfMonth;

    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "next_payment_date", nullable = false)
    private OffsetDateTime nextPaymentDate;

    @Column(name = "last_payment_date")
    private OffsetDateTime lastPaymentDate;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "auto_pay_enabled", nullable = false)
    private boolean autoPayEnabled = false;

    @Column(name = "failure_retry_count", nullable = false)
    private Short failureRetryCount = 0;

    @Column(name = "times_executed", nullable = false)
    private Integer timesExecuted = 0;

    @Column(name = "notify_days_in_advance", nullable = false)
    private Short notifyDaysInAdvance = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledStatus status = ScheduledStatus.ACTIVE;

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum Frequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        CUSTOM
    }

    public enum ScheduledStatus {
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED
    }
}