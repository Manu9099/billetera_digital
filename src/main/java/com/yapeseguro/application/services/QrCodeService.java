package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateQrCodeRequest;
import com.yapeseguro.api.dto.response.QrCodeResponse;
import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.InventoryItemEntity;
import com.yapeseguro.infrastructure.persistence.entities.QrCodeEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessProfileRepository;
import com.yapeseguro.infrastructure.persistence.repositories.InventoryItemRepository;
import com.yapeseguro.infrastructure.persistence.repositories.QrCodeRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final UserRepository userRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final QrCodeRepository qrCodeRepository;

    @Transactional
    public QrCodeResponse createBusinessQr(
            CreateQrCodeRequest request,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);
        CreateQrCodeRequest safeRequest = safeRequest(request);

        WalletEntity businessWallet = businessProfile.getBusinessWallet();

        QrCodeEntity.QrType qrType = safeRequest.getFixedAmount() != null
                ? QrCodeEntity.QrType.FIXED_AMOUNT
                : QrCodeEntity.QrType.PAYMENT;

        QrCodeEntity qrCode = QrCodeEntity.builder()
                .creatorUser(businessProfile.getUser())
                .creatorWallet(businessWallet)
                .qrType(qrType)
                .qrData("PENDING")
                .qrImageUrl(normalize(safeRequest.getQrImageUrl()))
                .description(normalize(safeRequest.getDescription()))
                .fixedAmount(safeRequest.getFixedAmount())
                .currency(resolveCurrency(safeRequest.getCurrency(), businessWallet))
                .scansCount(0)
                .paymentsCount(0)
                .revenue(BigDecimal.ZERO)
                .active(true)
                .build();

        QrCodeEntity savedQrCode = qrCodeRepository.save(qrCode);

        savedQrCode.setQrData(
                buildQrData(savedQrCode, businessProfile.getId(), null)
        );

        return toResponse(
                qrCodeRepository.save(savedQrCode),
                businessProfile.getId(),
                null
        );
    }

    @Transactional
    public QrCodeResponse createInventoryItemQr(
            UUID itemId,
            CreateQrCodeRequest request,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        InventoryItemEntity item = inventoryItemRepository
                .findByIdAndBusinessProfileAndActiveTrue(itemId, businessProfile)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        CreateQrCodeRequest safeRequest = safeRequest(request);

        QrCodeEntity qrCode = item.getQrCode();

        if (qrCode == null || !qrCode.isActive()) {
            qrCode = QrCodeEntity.builder()
                    .creatorUser(businessProfile.getUser())
                    .creatorWallet(businessProfile.getBusinessWallet())
                    .qrType(QrCodeEntity.QrType.INVENTORY)
                    .qrData("PENDING")
                    .qrImageUrl(normalize(safeRequest.getQrImageUrl()))
                    .description(resolveInventoryQrDescription(item, safeRequest))
                    .fixedAmount(item.getPrice())
                    .currency(businessProfile.getBusinessWallet().getCurrency())
                    .scansCount(0)
                    .paymentsCount(0)
                    .revenue(BigDecimal.ZERO)
                    .active(true)
                    .build();

            qrCode = qrCodeRepository.save(qrCode);
        } else {
            qrCode.setQrType(QrCodeEntity.QrType.INVENTORY);
            qrCode.setDescription(resolveInventoryQrDescription(item, safeRequest));
            qrCode.setFixedAmount(item.getPrice());
            qrCode.setCurrency(businessProfile.getBusinessWallet().getCurrency());

            if (safeRequest.getQrImageUrl() != null) {
                qrCode.setQrImageUrl(normalize(safeRequest.getQrImageUrl()));
            }

            qrCode.setActive(true);
        }

        qrCode.setQrData(
                buildQrData(qrCode, businessProfile.getId(), item.getId())
        );

        QrCodeEntity savedQrCode = qrCodeRepository.save(qrCode);

        item.setQrCode(savedQrCode);
        item.setQrEnabled(true);

        inventoryItemRepository.save(item);

        return toResponse(savedQrCode, businessProfile.getId(), item.getId());
    }

    @Transactional(readOnly = true)
    public List<QrCodeResponse> getMyQrCodes(String username) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        return qrCodeRepository
                .findByCreatorWalletAndActiveTrueOrderByCreatedAtDesc(
                        businessProfile.getBusinessWallet()
                )
                .stream()
                .map(qrCode -> toResponse(
                        qrCode,
                        businessProfile.getId(),
                        resolveInventoryItemId(businessProfile, qrCode)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public QrCodeResponse getMyQrCode(
            UUID qrId,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        QrCodeEntity qrCode = qrCodeRepository
                .findByIdAndCreatorWalletAndActiveTrue(
                        qrId,
                        businessProfile.getBusinessWallet()
                )
                .orElseThrow(() -> new IllegalArgumentException("QR no encontrado"));

        return toResponse(
                qrCode,
                businessProfile.getId(),
                resolveInventoryItemId(businessProfile, qrCode)
        );
    }

    @Transactional
    public void deactivateQrCode(
            UUID qrId,
            String username
    ) {
        BusinessProfileEntity businessProfile = getActiveBusinessProfile(username);

        QrCodeEntity qrCode = qrCodeRepository
                .findByIdAndCreatorWalletAndActiveTrue(
                        qrId,
                        businessProfile.getBusinessWallet()
                )
                .orElseThrow(() -> new IllegalArgumentException("QR no encontrado"));

        qrCode.setActive(false);

        qrCodeRepository.save(qrCode);

        inventoryItemRepository
                .findByBusinessProfileAndQrCode(businessProfile, qrCode)
                .ifPresent(item -> {
                    item.setQrEnabled(false);
                    item.setQrCode(null);
                    inventoryItemRepository.save(item);
                });
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

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private CreateQrCodeRequest safeRequest(CreateQrCodeRequest request) {
        return request != null ? request : new CreateQrCodeRequest();
    }

    private String resolveCurrency(String requestCurrency, WalletEntity businessWallet) {
        String normalizedCurrency = normalize(requestCurrency);

        if (normalizedCurrency != null) {
            return normalizedCurrency.toUpperCase();
        }

        return businessWallet.getCurrency();
    }

    private String resolveInventoryQrDescription(
            InventoryItemEntity item,
            CreateQrCodeRequest request
    ) {
        String requestDescription = normalize(request.getDescription());

        if (requestDescription != null) {
            return requestDescription;
        }

        return "QR de producto: " + item.getProductName();
    }

    private UUID resolveInventoryItemId(
            BusinessProfileEntity businessProfile,
            QrCodeEntity qrCode
    ) {
        return inventoryItemRepository
                .findByBusinessProfileAndQrCode(businessProfile, qrCode)
                .map(InventoryItemEntity::getId)
                .orElse(null);
    }

    private String buildQrData(
            QrCodeEntity qrCode,
            UUID businessProfileId,
            UUID inventoryItemId
    ) {
        StringBuilder data = new StringBuilder();

        data.append("YAPESEGURO");
        data.append("|QR_ID=").append(qrCode.getId());
        data.append("|TYPE=").append(qrCode.getQrType().name());
        data.append("|BUSINESS_ID=").append(businessProfileId);
        data.append("|WALLET_ID=").append(qrCode.getCreatorWallet().getId());
        data.append("|CURRENCY=").append(qrCode.getCurrency());

        if (qrCode.getFixedAmount() != null) {
            data.append("|AMOUNT=").append(qrCode.getFixedAmount());
        }

        if (inventoryItemId != null) {
            data.append("|ITEM_ID=").append(inventoryItemId);
        }

        return data.toString();
    }

    private QrCodeResponse toResponse(
            QrCodeEntity qrCode,
            UUID businessProfileId,
            UUID inventoryItemId
    ) {
        return QrCodeResponse.builder()
                .id(qrCode.getId())
                .creatorUserId(qrCode.getCreatorUser().getId())
                .creatorWalletId(qrCode.getCreatorWallet().getId())
                .businessProfileId(businessProfileId)
                .inventoryItemId(inventoryItemId)
                .qrType(qrCode.getQrType().name())
                .qrData(qrCode.getQrData())
                .qrImageUrl(qrCode.getQrImageUrl())
                .description(qrCode.getDescription())
                .fixedAmount(qrCode.getFixedAmount())
                .currency(qrCode.getCurrency())
                .scansCount(qrCode.getScansCount())
                .paymentsCount(qrCode.getPaymentsCount())
                .revenue(qrCode.getRevenue())
                .active(qrCode.isActive())
                .createdAt(qrCode.getCreatedAt())
                .updatedAt(qrCode.getUpdatedAt())
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}