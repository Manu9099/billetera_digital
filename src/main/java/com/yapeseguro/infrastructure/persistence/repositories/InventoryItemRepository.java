package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.InventoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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
}