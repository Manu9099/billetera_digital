package com.yapeseguro.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name = "users", schema = "yape")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "reniec_id", unique = true, length = 8)
    private String reniecId;

    @Column(name = "kyc_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_document_url")
    private String kycDocumentUrl;

    @Column(name = "kyc_verified_at")
    private OffsetDateTime kycVerifiedAt;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "biometric_enabled", nullable = false)
    private boolean biometricEnabled = false;

    @Column(name = "interface_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    private InterfaceMode interfaceMode = InterfaceMode.STANDARD;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum KycStatus   { PENDING, VERIFIED, REJECTED }
    public enum InterfaceMode { STANDARD, SENIOR_MODE }
}