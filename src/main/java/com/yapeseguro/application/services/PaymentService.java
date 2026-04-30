package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.QrPaymentRequest;
import com.yapeseguro.api.dto.response.QrPaymentPreviewResponse;
import com.yapeseguro.api.dto.response.QrPaymentResponse;
import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.InventoryItemEntity;
import com.yapeseguro.infrastructure.persistence.entities.QrCodeEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessProfileRepository;
import com.yapeseguro.infrastructure.persistence.repositories.InventoryItemRepository;
import com.yapeseguro.infrastructure.persistence.repositories.QrCodeRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String DEFAULT_CURRENCY = "PEN";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final QrCodeRepository qrCodeRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public QrPaymentPreviewResponse previewBusinessQrPayment(UUID qrCodeId) {
        QrCodeEntity qrCode = qrCodeRepository.findByIdAndActiveTrue(qrCodeId)
                .orElseThrow(() -> new IllegalArgumentException("QR no encontrado o inactivo"));

        BusinessProfileEntity businessProfile = getActiveBusinessProfileByWallet(qrCode.getCreatorWallet());

        InventoryItemEntity inventoryItem = resolveInventoryItem(qrCode);

        qrCode.setScansCount(qrCode.getScansCount() + 1);
        qrCodeRepository.save(qrCode);

        BigDecimal amount = resolvePreviewAmount(qrCode, inventoryItem);

        return QrPaymentPreviewResponse.builder()
                .qrCodeId(qrCode.getId())
                .qrType(qrCode.getQrType().name())
                .businessProfileId(businessProfile.getId())
                .businessWalletId(businessProfile.getBusinessWallet().getId())
                .businessName(businessProfile.getBusinessName())
                .businessRuc(businessProfile.getRuc())
                .businessCategory(businessProfile.getBusinessCategory())
                .inventoryItemId(inventoryItem != null ? inventoryItem.getId() : null)
                .productName(inventoryItem != null ? inventoryItem.getProductName() : null)
                .productCategory(inventoryItem != null ? inventoryItem.getProductCategory() : null)
                .imageUrl(inventoryItem != null ? inventoryItem.getImageUrl() : null)
                .currentStock(inventoryItem != null ? inventoryItem.getCurrentStock() : null)
                .amount(amount)
                .currency(qrCode.getCurrency())
                .description(resolvePreviewDescription(qrCode, inventoryItem))
                .fixedAmount(qrCode.getFixedAmount() != null)
                .inventoryPayment(qrCode.getQrType() == QrCodeEntity.QrType.INVENTORY)
                .available(isAvailable(qrCode, inventoryItem))
                .build();
    }

    @Transactional
    public QrPaymentResponse payBusinessQr(
            QrPaymentRequest request,
            String username
    ) {
        UserEntity buyer = getUserByUsername(username);

        QrCodeEntity qrCode = qrCodeRepository.findByIdForUpdate(request.getQrCodeId())
                .orElseThrow(() -> new IllegalArgumentException("QR no encontrado"));

        if (!qrCode.isActive()) {
            throw new IllegalArgumentException("El QR no está activo");
        }

        BusinessProfileEntity businessProfile = getActiveBusinessProfileByWallet(qrCode.getCreatorWallet());

        if (businessProfile.getUser().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("No puedes pagar tu propio QR de negocio");
        }

        WalletEntity buyerWalletRef = walletRepository
                .findByUserAndWalletType(buyer, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("No tienes billetera personal"));

        WalletPair lockedWallets = lockWalletsInStableOrder(
                buyerWalletRef.getId(),
                businessProfile.getBusinessWallet().getId()
        );

        WalletEntity sourceWallet = lockedWallets.sourceWallet();
        WalletEntity businessWallet = lockedWallets.targetWallet();

        validateWalletIsActive(sourceWallet, "Tu billetera personal no está activa");
        validateWalletIsActive(businessWallet, "La billetera del negocio no está activa");

        if (sourceWallet.getWalletType() != WalletEntity.WalletType.PERSONAL) {
            throw new IllegalArgumentException("El pago debe salir de una billetera personal");
        }

        if (businessWallet.getWalletType() != WalletEntity.WalletType.BUSINESS) {
            throw new IllegalArgumentException("El QR no pertenece a una billetera de negocio");
        }

        if (!sourceWallet.getCurrency().equals(businessWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        if (!sourceWallet.getCurrency().equals(resolveCurrency(qrCode.getCurrency()))) {
            throw new IllegalArgumentException("La moneda del QR no coincide con tu billetera");
        }

        InventoryItemEntity inventoryItem = resolveInventoryItemForPayment(qrCode);

        BigDecimal amount = resolvePaymentAmount(qrCode, inventoryItem, request);

        if (safe(sourceWallet.getAvailableBalance()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        OffsetDateTime now = OffsetDateTime.now();

        applyDebit(sourceWallet, amount);
        applyCredit(businessWallet, amount);

        sourceWallet.setLastTransactionAt(now);
        businessWallet.setLastTransactionAt(now);

        qrCode.setPaymentsCount(qrCode.getPaymentsCount() + 1);
        qrCode.setRevenue(safe(qrCode.getRevenue()).add(amount));

        if (inventoryItem != null) {
            applyInventorySale(inventoryItem, amount, now);
        }

        businessProfile.setTotalTransactions(businessProfile.getTotalTransactions() + 1);
        businessProfile.setTotalRevenue(safe(businessProfile.getTotalRevenue()).add(amount));

        walletRepository.saveAll(List.of(sourceWallet, businessWallet));
        qrCodeRepository.save(qrCode);
        businessProfileRepository.save(businessProfile);

        if (inventoryItem != null) {
            inventoryItemRepository.save(inventoryItem);
        }

        TransactionEntity transaction = TransactionEntity.builder()
                .walletFrom(sourceWallet)
                .walletTo(businessWallet)
                .amount(amount)
                .currency(sourceWallet.getCurrency())
                .type(TransactionEntity.TxType.QR_PAYMENT)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .description(resolvePaymentDescription(qrCode, inventoryItem, request))
                .concept("Pago QR")
                .reference(generateUniqueReference())
                .qrCode(qrCode)
                .qrDescription(qrCode.getDescription())
                .qrFixedAmount(qrCode.getFixedAmount())
                .notes(normalize(request.getNotes()))
                .completedAt(now)
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        return toPaymentResponse(
                savedTransaction,
                buyer,
                businessProfile,
                inventoryItem
        );
    }

    private InventoryItemEntity resolveInventoryItem(QrCodeEntity qrCode) {
        if (qrCode.getQrType() != QrCodeEntity.QrType.INVENTORY) {
            return null;
        }

        return inventoryItemRepository
                .findByQrCodeAndActiveTrue(qrCode)
                .orElse(null);
    }

    private InventoryItemEntity resolveInventoryItemForPayment(QrCodeEntity qrCode) {
        if (qrCode.getQrType() != QrCodeEntity.QrType.INVENTORY) {
            return null;
        }

        InventoryItemEntity item = inventoryItemRepository
                .findActiveByQrCodeForUpdate(qrCode)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado para este QR"));

        if (item.getCurrentStock() <= 0) {
            throw new IllegalArgumentException("Producto sin stock disponible");
        }

        return item;
    }

    private BigDecimal resolvePreviewAmount(
            QrCodeEntity qrCode,
            InventoryItemEntity inventoryItem
    ) {
        if (inventoryItem != null) {
            return inventoryItem.getPrice();
        }

        return qrCode.getFixedAmount();
    }

    private BigDecimal resolvePaymentAmount(
            QrCodeEntity qrCode,
            InventoryItemEntity inventoryItem,
            QrPaymentRequest request
    ) {
        if (inventoryItem != null) {
            return inventoryItem.getPrice();
        }

        if (qrCode.getFixedAmount() != null) {
            return qrCode.getFixedAmount();
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto es obligatorio para QR sin monto fijo");
        }

        return request.getAmount();
    }

    private String resolvePreviewDescription(
            QrCodeEntity qrCode,
            InventoryItemEntity inventoryItem
    ) {
        if (inventoryItem != null) {
            return "Pago por producto: " + inventoryItem.getProductName();
        }

        String qrDescription = normalize(qrCode.getDescription());

        return qrDescription != null ? qrDescription : "Pago QR a negocio";
    }

    private String resolvePaymentDescription(
            QrCodeEntity qrCode,
            InventoryItemEntity inventoryItem,
            QrPaymentRequest request
    ) {
        String requestDescription = normalize(request.getDescription());

        if (requestDescription != null) {
            return requestDescription;
        }

        return resolvePreviewDescription(qrCode, inventoryItem);
    }

    private boolean isAvailable(
            QrCodeEntity qrCode,
            InventoryItemEntity inventoryItem
    ) {
        if (!qrCode.isActive()) {
            return false;
        }

        if (inventoryItem == null) {
            return true;
        }

        return inventoryItem.isActive() && inventoryItem.getCurrentStock() > 0;
    }

    private BusinessProfileEntity getActiveBusinessProfileByWallet(WalletEntity businessWallet) {
        BusinessProfileEntity businessProfile = businessProfileRepository
                .findByBusinessWalletAndActiveTrue(businessWallet)
                .orElseThrow(() -> new IllegalArgumentException("Perfil de negocio no encontrado o inactivo"));

        if (!businessProfile.getBusinessWallet().isActive()) {
            throw new IllegalArgumentException("La billetera de negocio no está activa");
        }

        return businessProfile;
    }

    private WalletPair lockWalletsInStableOrder(UUID sourceWalletId, UUID targetWalletId) {
        WalletEntity firstLocked;
        WalletEntity secondLocked;

        if (sourceWalletId.compareTo(targetWalletId) <= 0) {
            firstLocked = lockWallet(sourceWalletId);
            secondLocked = lockWallet(targetWalletId);
        } else {
            firstLocked = lockWallet(targetWalletId);
            secondLocked = lockWallet(sourceWalletId);
        }

        WalletEntity sourceWallet = firstLocked.getId().equals(sourceWalletId)
                ? firstLocked
                : secondLocked;

        WalletEntity targetWallet = firstLocked.getId().equals(targetWalletId)
                ? firstLocked
                : secondLocked;

        return new WalletPair(sourceWallet, targetWallet);
    }

    private WalletEntity lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada"));
    }

    private void validateWalletIsActive(WalletEntity wallet, String message) {
        if (!wallet.isActive()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void applyDebit(WalletEntity wallet, BigDecimal amount) {
        wallet.setBalance(safe(wallet.getBalance()).subtract(amount));
        wallet.setAvailableBalance(safe(wallet.getAvailableBalance()).subtract(amount));
        wallet.setMonthlyExpenses(safe(wallet.getMonthlyExpenses()).add(amount));
        wallet.setDailyTxCount(wallet.getDailyTxCount() + 1);
    }

    private void applyCredit(WalletEntity wallet, BigDecimal amount) {
        wallet.setBalance(safe(wallet.getBalance()).add(amount));
        wallet.setAvailableBalance(safe(wallet.getAvailableBalance()).add(amount));
        wallet.setMonthlyRevenue(safe(wallet.getMonthlyRevenue()).add(amount));
        wallet.setDailyTxCount(wallet.getDailyTxCount() + 1);
    }

    private void applyInventorySale(
            InventoryItemEntity item,
            BigDecimal amount,
            OffsetDateTime now
    ) {
        if (item.getCurrentStock() <= 0) {
            throw new IllegalArgumentException("Producto sin stock disponible");
        }

        item.setCurrentStock(item.getCurrentStock() - 1);
        item.setTotalUnitsSold(item.getTotalUnitsSold() + 1);
        item.setSoldThisMonth(item.getSoldThisMonth() + 1);
        item.setSoldThisWeek(item.getSoldThisWeek() + 1);
        item.setRevenueThisMonth(safe(item.getRevenueThisMonth()).add(amount));
        item.setRevenueThisWeek(safe(item.getRevenueThisWeek()).add(amount));
        item.setLastSoldAt(now);
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private String generateUniqueReference() {
        String reference;

        do {
            reference = "QR-"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase();
        } while (transactionRepository.existsByReference(reference));

        return reference;
    }

    private QrPaymentResponse toPaymentResponse(
            TransactionEntity transaction,
            UserEntity buyer,
            BusinessProfileEntity businessProfile,
            InventoryItemEntity inventoryItem
    ) {
        return QrPaymentResponse.builder()
                .transactionId(transaction.getId())
                .reference(transaction.getReference())
                .qrCodeId(transaction.getQrCode().getId())
                .qrType(transaction.getQrCode().getQrType().name())
                .buyerUserId(buyer.getId())
                .buyerName(fullName(buyer))
                .sourceWalletId(transaction.getWalletFrom().getId())
                .businessProfileId(businessProfile.getId())
                .businessName(businessProfile.getBusinessName())
                .businessRuc(businessProfile.getRuc())
                .businessWalletId(transaction.getWalletTo().getId())
                .inventoryItemId(inventoryItem != null ? inventoryItem.getId() : null)
                .productName(inventoryItem != null ? inventoryItem.getProductName() : null)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus().name())
                .description(transaction.getDescription())
                .notes(transaction.getNotes())
                .remainingStock(inventoryItem != null ? inventoryItem.getCurrentStock() : null)
                .completedAt(transaction.getCompletedAt())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private String fullName(UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private String resolveCurrency(String currency) {
        String normalized = normalize(currency);

        return normalized != null ? normalized.toUpperCase() : DEFAULT_CURRENCY;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private record WalletPair(
            WalletEntity sourceWallet,
            WalletEntity targetWallet
    ) {
    }
}