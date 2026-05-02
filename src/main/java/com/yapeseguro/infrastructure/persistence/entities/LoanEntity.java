package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loans", schema = "yape")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_user_id", nullable = false)
    private UserEntity borrowerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_user_id", nullable = false)
    private UserEntity lenderUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "remaining_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal remainingBalance;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Column(name = "total_amount_to_return", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmountToReturn;

    @Column(name = "late_fee_per_day", nullable = false, precision = 14, scale = 2)
    private BigDecimal lateFeePerDay = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_status", nullable = false, length = 20)
    private LoanStatus loanStatus = LoanStatus.ACTIVE;

    @Column(name = "loan_date", nullable = false)
    private OffsetDateTime loanDate;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "completed_date")
    private OffsetDateTime completedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum LoanStatus {
        ACTIVE,
        COMPLETED,
        DEFAULT,
        CANCELLED
    }
}