package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_receipts", schema = "yape")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private TransactionEntity transaction;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @Column(name = "business_name", length = 255)
    private String businessName;

    @Column(name = "business_ruc", length = 11)
    private String businessRuc;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String concept;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "receipt_html", columnDefinition = "TEXT")
    private String receiptHtml;

    @Column(name = "receipt_pdf_url", columnDefinition = "TEXT")
    private String receiptPdfUrl;

    @Column(name = "qr_code_url", columnDefinition = "TEXT")
    private String qrCodeUrl;

    @Column(name = "printed_count", nullable = false)
    private Integer printedCount = 0;

    @Column(name = "emailed_to", length = 255)
    private String emailedTo;

    @Column(name = "sent_whatsapp", nullable = false)
    private boolean sentWhatsapp = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}