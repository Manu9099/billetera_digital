package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.MarketplaceRequest;
import com.yapeseguro.api.dto.request.P2PRequest;
import com.yapeseguro.api.dto.response.PageResponse;
import com.yapeseguro.api.dto.response.TransactionReceiptResponse;
import com.yapeseguro.api.dto.response.TransactionResponse;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_MARKETPLACE_HOLD_DAYS = 7;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse sendP2P(P2PRequest request, String username) {
        UserEntity sender = getUserByUsername(username);

        UserEntity recipient = userRepository.findById(request.getRecipientUserId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario receptor no encontrado"));

        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("No puedes transferirte dinero a ti mismo");
        }

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }

        WalletEntity sourceWalletRef = walletRepository.findById(request.getSourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Billetera origen no encontrada"));

        validateWalletBelongsToUser(sourceWalletRef, sender);

        if (sourceWalletRef.getWalletType() != WalletEntity.WalletType.PERSONAL) {
            throw new IllegalArgumentException("Solo puedes transferir desde una billetera personal");
        }

        WalletEntity targetWalletRef = walletRepository
                .findByUserAndWalletType(recipient, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("El receptor no tiene billetera personal"));

        WalletPair lockedWallets = lockWalletsInStableOrder(
                sourceWalletRef.getId(),
                targetWalletRef.getId()
        );

        WalletEntity sourceWallet = lockedWallets.sourceWallet();
        WalletEntity targetWallet = lockedWallets.targetWallet();

        validateWalletIsActive(sourceWallet, "La billetera origen no está activa");
        validateWalletIsActive(targetWallet, "La billetera destino no está activa");

        if (targetWallet.getWalletType() != WalletEntity.WalletType.PERSONAL) {
            throw new IllegalArgumentException("La billetera destino debe ser personal");
        }

        if (!sourceWallet.getCurrency().equals(targetWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        if (safe(sourceWallet.getAvailableBalance()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        OffsetDateTime now = OffsetDateTime.now();

        applyDebit(sourceWallet, amount);
        applyCredit(targetWallet, amount);

        sourceWallet.setLastTransactionAt(now);
        targetWallet.setLastTransactionAt(now);

        walletRepository.saveAll(List.of(sourceWallet, targetWallet));

        TransactionEntity transaction = TransactionEntity.builder()
                .walletFrom(sourceWallet)
                .walletTo(targetWallet)
                .amount(amount)
                .currency(sourceWallet.getCurrency())
                .type(TransactionEntity.TxType.P2P)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .description("Transferencia P2P")
                .concept(request.getConcept())
                .notes(request.getNotes())
                .reference(generateUniqueReference("P2P"))
                .completedAt(now)
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse createMarketplacePayment(
            MarketplaceRequest request,
            String username
    ) {
        UserEntity buyer = getUserByUsername(username);

        UserEntity seller = userRepository.findById(request.getSellerUserId())
                .orElseThrow(() -> new IllegalArgumentException("Vendedor no encontrado"));

        if (buyer.getId().equals(seller.getId())) {
            throw new IllegalArgumentException("No puedes crear un pago protegido hacia ti mismo");
        }

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }

        WalletEntity buyerWalletRef = walletRepository
                .findByUserAndWalletType(buyer, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("No tienes billetera personal"));

        WalletEntity sellerBusinessWalletRef = walletRepository
                .findByUserAndWalletType(seller, WalletEntity.WalletType.BUSINESS)
                .orElseThrow(() -> new IllegalArgumentException("El vendedor no tiene billetera de negocio"));

        WalletPair lockedWallets = lockWalletsInStableOrder(
                buyerWalletRef.getId(),
                sellerBusinessWalletRef.getId()
        );

        WalletEntity buyerWallet = lockedWallets.sourceWallet();
        WalletEntity sellerBusinessWallet = lockedWallets.targetWallet();

        validateWalletIsActive(buyerWallet, "Tu billetera personal no está activa");
        validateWalletIsActive(sellerBusinessWallet, "La billetera de negocio del vendedor no está activa");

        if (buyerWallet.getWalletType() != WalletEntity.WalletType.PERSONAL) {
            throw new IllegalArgumentException("El pago protegido debe salir de una billetera personal");
        }

        if (sellerBusinessWallet.getWalletType() != WalletEntity.WalletType.BUSINESS) {
            throw new IllegalArgumentException("El vendedor debe recibir en una billetera de negocio");
        }

        if (!buyerWallet.getCurrency().equals(sellerBusinessWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        if (safe(buyerWallet.getAvailableBalance()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime holdExpiresAt = now.plusDays(resolveMarketplaceHoldDays(request.getHoldDays()));

        applyDebit(buyerWallet, amount);
        applyMarketplaceHold(sellerBusinessWallet, amount);

        buyerWallet.setLastTransactionAt(now);
        sellerBusinessWallet.setLastTransactionAt(now);

        walletRepository.saveAll(List.of(buyerWallet, sellerBusinessWallet));

        TransactionEntity transaction = TransactionEntity.builder()
                .walletFrom(buyerWallet)
                .walletTo(sellerBusinessWallet)
                .amount(amount)
                .currency(buyerWallet.getCurrency())
                .type(TransactionEntity.TxType.MARKETPLACE)
                .status(TransactionEntity.TxStatus.HELD)
                .marketplaceStatus(TransactionEntity.MpStatus.HELD)
                .description(normalizeRequired(
                        request.getProductDescription(),
                        "La descripción del producto es obligatoria"
                ))
                .concept("Yape Seguro")
                .reference(generateUniqueReference("MKT"))
                .notes(normalize(request.getNotes()))
                .holdExpiresAt(holdExpiresAt)
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse confirmMarketplaceReceipt(
            UUID transactionId,
            String username
    ) {
        UserEntity buyer = getUserByUsername(username);

        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        if (transaction.getType() != TransactionEntity.TxType.MARKETPLACE) {
            throw new IllegalArgumentException("La transacción no pertenece a marketplace");
        }

        if (!transaction.getWalletFrom().getUser().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("Solo el comprador puede confirmar la recepción");
        }

        if (transaction.getMarketplaceStatus() == TransactionEntity.MpStatus.DISPUTED) {
            throw new IllegalArgumentException("La transacción está en disputa");
        }

        if (transaction.getStatus() == TransactionEntity.TxStatus.RELEASED
                || transaction.getStatus() == TransactionEntity.TxStatus.COMPLETED
                || transaction.getMarketplaceStatus() == TransactionEntity.MpStatus.BUYER_CONFIRMED) {
            throw new IllegalArgumentException("La recepción ya fue confirmada");
        }

        if (transaction.getStatus() != TransactionEntity.TxStatus.HELD
                || transaction.getMarketplaceStatus() != TransactionEntity.MpStatus.HELD) {
            throw new IllegalArgumentException("La transacción no está retenida");
        }

        WalletEntity sellerBusinessWallet = walletRepository
                .findByIdForUpdate(transaction.getWalletTo().getId())
                .orElseThrow(() -> new IllegalArgumentException("Billetera de negocio no encontrada"));

        validateWalletIsActive(sellerBusinessWallet, "La billetera de negocio del vendedor no está activa");

        BigDecimal amount = transaction.getAmount();

        if (safe(sellerBusinessWallet.getHoldAmount()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("El monto retenido no es suficiente para liberar la operación");
        }

        OffsetDateTime now = OffsetDateTime.now();

        releaseMarketplaceHold(sellerBusinessWallet, amount);
        sellerBusinessWallet.setLastTransactionAt(now);

        transaction.setStatus(TransactionEntity.TxStatus.RELEASED);
        transaction.setMarketplaceStatus(TransactionEntity.MpStatus.BUYER_CONFIRMED);
        transaction.setCompletedAt(now);

        walletRepository.save(sellerBusinessWallet);

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public int releaseExpiredMarketplaceHoldsAutomatically() {
        OffsetDateTime now = OffsetDateTime.now();

        List<TransactionEntity> expiredHolds = transactionRepository.findExpiredMarketplaceHoldsForUpdate(now);

        int released = 0;

        for (TransactionEntity transaction : expiredHolds) {
            try {
                releaseExpiredMarketplaceHold(transaction, now);
                released++;
            } catch (RuntimeException ex) {
                log.warn(
                        "Could not auto-release marketplace transaction {}: {}",
                        transaction.getId(),
                        ex.getMessage()
                );
            }
        }

        return released;
    }

    @Transactional(readOnly = true)
    public PageResponse getWalletTransactions(
            UUID walletId,
            String username,
            int page,
            int size
    ) {
        UserEntity user = getUserByUsername(username);

        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada"));

        validateWalletBelongsToUser(wallet, user);

        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<TransactionResponse> transactions = transactionRepository
                .findHistoryByWallet(wallet, pageRequest)
                .map(this::toResponse);

        return toPageResponse(transactions);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId, String username) {
        UserEntity user = getUserByUsername(username);

        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionBelongsToUser(transaction, user);

        return toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionReceiptResponse getReceipt(UUID transactionId, String username) {
        UserEntity user = getUserByUsername(username);

        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionBelongsToUser(transaction, user);

        return toReceiptResponse(transaction);
    }

    private void releaseExpiredMarketplaceHold(
            TransactionEntity transaction,
            OffsetDateTime now
    ) {
        if (transaction.getMarketplaceDispute() != null) {
            return;
        }

        if (transaction.getStatus() != TransactionEntity.TxStatus.HELD
                || transaction.getMarketplaceStatus() != TransactionEntity.MpStatus.HELD) {
            return;
        }

        WalletEntity sellerBusinessWallet = walletRepository
                .findByIdForUpdate(transaction.getWalletTo().getId())
                .orElseThrow(() -> new IllegalArgumentException("Billetera de negocio no encontrada"));

        BigDecimal amount = transaction.getAmount();

        if (safe(sellerBusinessWallet.getHoldAmount()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Hold insuficiente para liberar operación vencida");
        }

        releaseMarketplaceHold(sellerBusinessWallet, amount);
        sellerBusinessWallet.setLastTransactionAt(now);

        transaction.setStatus(TransactionEntity.TxStatus.RELEASED);
        transaction.setMarketplaceStatus(TransactionEntity.MpStatus.BUYER_CONFIRMED);
        transaction.setCompletedAt(now);
        transaction.setNotes(appendNote(
                transaction.getNotes(),
                "Hold vencido: liberación automática al vendedor."
        ));

        walletRepository.save(sellerBusinessWallet);
        transactionRepository.save(transaction);
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

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private void validateWalletBelongsToUser(WalletEntity wallet, UserEntity user) {
        if (!wallet.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("No tienes permiso para usar esta billetera");
        }
    }

    private void validateTransactionBelongsToUser(TransactionEntity transaction, UserEntity user) {
        UUID userId = user.getId();

        UUID senderUserId = transaction.getWalletFrom().getUser().getId();
        UUID recipientUserId = transaction.getWalletTo().getUser().getId();

        if (!senderUserId.equals(userId) && !recipientUserId.equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para ver esta transacción");
        }
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

    private void applyMarketplaceHold(WalletEntity sellerBusinessWallet, BigDecimal amount) {
        sellerBusinessWallet.setHoldAmount(safe(sellerBusinessWallet.getHoldAmount()).add(amount));
        sellerBusinessWallet.setDailyTxCount(sellerBusinessWallet.getDailyTxCount() + 1);
    }

    private void releaseMarketplaceHold(WalletEntity sellerBusinessWallet, BigDecimal amount) {
        sellerBusinessWallet.setHoldAmount(safe(sellerBusinessWallet.getHoldAmount()).subtract(amount));
        sellerBusinessWallet.setBalance(safe(sellerBusinessWallet.getBalance()).add(amount));
        sellerBusinessWallet.setAvailableBalance(safe(sellerBusinessWallet.getAvailableBalance()).add(amount));
        sellerBusinessWallet.setMonthlyRevenue(safe(sellerBusinessWallet.getMonthlyRevenue()).add(amount));
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String generateUniqueReference(String prefix) {
        String reference;

        do {
            reference = prefix
                    + "-"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase();
        } while (transactionRepository.existsByReference(reference));

        return reference;
    }

    private TransactionResponse toResponse(TransactionEntity transaction) {
        WalletEntity walletFrom = transaction.getWalletFrom();
        WalletEntity walletTo = transaction.getWalletTo();

        UserEntity sender = walletFrom.getUser();
        UserEntity recipient = walletTo.getUser();

        return TransactionResponse.builder()
                .id(transaction.getId())
                .walletFromId(walletFrom.getId())
                .walletToId(walletTo.getId())
                .senderUserId(sender.getId())
                .recipientUserId(recipient.getId())
                .senderName(fullName(sender))
                .recipientName(fullName(recipient))
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType().name())
                .status(transaction.getStatus().name())
                .marketplaceStatus(transaction.getMarketplaceStatus().name())
                .marketplaceDisputeId(
                        transaction.getMarketplaceDispute() != null
                                ? transaction.getMarketplaceDispute().getId()
                                : null
                )
                .description(transaction.getDescription())
                .concept(transaction.getConcept())
                .reference(transaction.getReference())
                .notes(transaction.getNotes())
                .holdExpiresAt(transaction.getHoldExpiresAt())
                .completedAt(transaction.getCompletedAt())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private TransactionReceiptResponse toReceiptResponse(TransactionEntity transaction) {
        WalletEntity walletFrom = transaction.getWalletFrom();
        WalletEntity walletTo = transaction.getWalletTo();

        UserEntity sender = walletFrom.getUser();
        UserEntity recipient = walletTo.getUser();

        return TransactionReceiptResponse.builder()
                .transactionId(transaction.getId())
                .reference(transaction.getReference())
                .senderUserId(sender.getId())
                .recipientUserId(recipient.getId())
                .senderName(fullName(sender))
                .recipientName(fullName(recipient))
                .walletFromId(walletFrom.getId())
                .walletToId(walletTo.getId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType().name())
                .status(transaction.getStatus().name())
                .marketplaceStatus(transaction.getMarketplaceStatus().name())
                .concept(transaction.getConcept())
                .description(transaction.getDescription())
                .notes(transaction.getNotes())
                .completedAt(transaction.getCompletedAt())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private PageResponse toPageResponse(Page<TransactionResponse> page) {
        return PageResponse.builder()
                .content(Collections.singletonList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int resolveMarketplaceHoldDays(int holdDays) {
        if (holdDays <= 0) {
            return DEFAULT_MARKETPLACE_HOLD_DAYS;
        }

        return holdDays;
    }

    private String fullName(UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
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

    private String appendNote(String currentNotes, String newNote) {
        String normalizedCurrentNotes = normalize(currentNotes);

        if (normalizedCurrentNotes == null) {
            return newNote;
        }

        return normalizedCurrentNotes + " | " + newNote;
    }

    private record WalletPair(
            WalletEntity sourceWallet,
            WalletEntity targetWallet
    ) {
    }
}