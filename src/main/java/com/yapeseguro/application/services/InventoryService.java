package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.AddInventoryItemRequest;
import com.yapeseguro.api.dto.request.UpdateInventoryItemRequest;
import com.yapeseguro.api.dto.response.InventoryItemResponse;
import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.InventoryItemEntity;
import com.yapeseguro.infrastructure.persistence.entities.QrCodeEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessProfileRepository;
import com.yapeseguro.infrastructure.persistence.repositories.InventoryItemRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final UserRepository userRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getMyInventory(String username) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        return inventoryItemRepository
                .findByBusinessProfileAndActiveTrueOrderByCreatedAtDesc(businessProfile)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InventoryItemResponse addInventoryItem(
            AddInventoryItemRequest request,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        String sku = normalize(request.getSku());

        validateSkuIsAvailable(businessProfile, sku, null);

        InventoryItemEntity item = InventoryItemEntity.builder()
                .businessProfile(businessProfile)
                .productName(normalizeRequired(request.getProductName(), "El nombre del producto es obligatorio"))
                .description(normalize(request.getDescription()))
                .productCategory(normalize(request.getProductCategory()))
                .sku(sku)
                .imageUrl(normalize(request.getImageUrl()))
                .price(request.getPrice())
                .currentStock(request.getCurrentStock())
                .lowStockThreshold(resolveLowStockThreshold(request.getLowStockThreshold()))
                .totalUnitsSold(0)
                .qrEnabled(request.isQrEnabled())
                .soldThisMonth(0)
                .soldThisWeek(0)
                .revenueThisMonth(BigDecimal.ZERO)
                .revenueThisWeek(BigDecimal.ZERO)
                .active(true)
                .build();

        return toResponse(inventoryItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getInventoryItem(
            UUID itemId,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        InventoryItemEntity item = getActiveItemForBusiness(itemId, businessProfile);

        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse updateInventoryItem(
            UUID itemId,
            UpdateInventoryItemRequest request,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        InventoryItemEntity item = getActiveItemForBusiness(itemId, businessProfile);

        if (request.getProductName() != null) {
            item.setProductName(
                    normalizeRequired(request.getProductName(), "El nombre del producto no puede estar vacío")
            );
        }

        if (request.getDescription() != null) {
            item.setDescription(normalize(request.getDescription()));
        }

        if (request.getProductCategory() != null) {
            item.setProductCategory(normalize(request.getProductCategory()));
        }

        if (request.getSku() != null) {
            String sku = normalize(request.getSku());
            validateSkuIsAvailable(businessProfile, sku, item.getId());
            item.setSku(sku);
        }

        if (request.getImageUrl() != null) {
            item.setImageUrl(normalize(request.getImageUrl()));
        }

        if (request.getPrice() != null) {
            item.setPrice(request.getPrice());
        }

        if (request.getCurrentStock() != null) {
            item.setCurrentStock(request.getCurrentStock());
        }

        if (request.getLowStockThreshold() != null) {
            item.setLowStockThreshold(resolveLowStockThreshold(request.getLowStockThreshold()));
        }

        if (request.getQrEnabled() != null) {
            item.setQrEnabled(request.getQrEnabled());
        }

        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }

        return toResponse(inventoryItemRepository.save(item));
    }

    @Transactional
    public void deactivateInventoryItem(
            UUID itemId,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        InventoryItemEntity item = getActiveItemForBusiness(itemId, businessProfile);

        item.setActive(false);

        inventoryItemRepository.save(item);
    }

    private BusinessProfileEntity getActiveBusinessProfile(String username) {
        UserEntity user = getUserByUsername(username);

        BusinessProfileEntity businessProfile = businessProfileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("No tienes un perfil de negocio creado"));

        if (!businessProfile.isActive()) {
            throw new IllegalArgumentException("Tu perfil de negocio no está activo");
        }

        if (!businessProfile.getBusinessWallet().isActive()) {
            throw new IllegalArgumentException("Tu billetera de negocio no está activa");
        }

        return businessProfile;
    }

    private InventoryItemEntity getActiveItemForBusiness(
            UUID itemId,
            BusinessProfileEntity businessProfile
    ) {
        return inventoryItemRepository
                .findByIdAndBusinessProfileAndActiveTrue(itemId, businessProfile)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
    }

    private void validateSkuIsAvailable(
            BusinessProfileEntity businessProfile,
            String sku,
            UUID currentItemId
    ) {
        if (sku == null) {
            return;
        }

        inventoryItemRepository.findByBusinessProfileAndSku(businessProfile, sku)
                .filter(existingItem -> currentItemId == null || !existingItem.getId().equals(currentItemId))
                .ifPresent(existingItem -> {
                    throw new IllegalArgumentException("Ya existe un producto con ese SKU");
                });
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private InventoryItemResponse toResponse(InventoryItemEntity item) {
        QrCodeEntity qrCode = item.getQrCode();

        return InventoryItemResponse.builder()
                .id(item.getId())
                .businessProfileId(item.getBusinessProfile().getId())
                .qrCodeId(qrCode != null ? qrCode.getId() : null)
                .productName(item.getProductName())
                .description(item.getDescription())
                .productCategory(item.getProductCategory())
                .sku(item.getSku())
                .imageUrl(item.getImageUrl())
                .price(item.getPrice())
                .currentStock(item.getCurrentStock())
                .lowStockThreshold(item.getLowStockThreshold())
                .lowStock(isLowStock(item))
                .totalUnitsSold(item.getTotalUnitsSold())
                .qrEnabled(item.isQrEnabled())
                .soldThisMonth(item.getSoldThisMonth())
                .soldThisWeek(item.getSoldThisWeek())
                .revenueThisMonth(item.getRevenueThisMonth())
                .revenueThisWeek(item.getRevenueThisWeek())
                .active(item.isActive())
                .lastSoldAt(item.getLastSoldAt())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private boolean isLowStock(InventoryItemEntity item) {
        return item.getCurrentStock() <= item.getLowStockThreshold();
    }

    private int resolveLowStockThreshold(Integer value) {
        return value != null ? value : 5;
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