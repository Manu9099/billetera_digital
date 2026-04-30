package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.InventoryItemEntity;
import com.yapeseguro.infrastructure.persistence.entities.QrCodeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, UUID> {

    List<InventoryItemEntity> findByBusinessProfileAndActiveTrueOrderByCreatedAtDesc(
            BusinessProfileEntity businessProfile
    );

    Optional<InventoryItemEntity> findByIdAndBusinessProfileAndActiveTrue(
            UUID id,
            BusinessProfileEntity businessProfile
    );

    Optional<InventoryItemEntity> findByBusinessProfileAndSku(
            BusinessProfileEntity businessProfile,
            String sku
    );

    Optional<InventoryItemEntity> findByBusinessProfileAndQrCode(
            BusinessProfileEntity businessProfile,
            QrCodeEntity qrCode
    );

    Optional<InventoryItemEntity> findByQrCodeAndActiveTrue(
            QrCodeEntity qrCode
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from InventoryItemEntity i
            join fetch i.businessProfile bp
            where i.qrCode = :qrCode
              and i.active = true
            """)
    Optional<InventoryItemEntity> findActiveByQrCodeForUpdate(
            @Param("qrCode") QrCodeEntity qrCode
    );
}