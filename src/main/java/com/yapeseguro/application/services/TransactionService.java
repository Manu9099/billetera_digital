package com.yapeseguro.application.services;

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

    @Service
    @RequiredArgsConstructor
    public class TransactionService {

        private static final int DEFAULT_PAGE_SIZE = 20;
        private static final int MAX_PAGE_SIZE = 50;

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
                    .reference(generateUniqueReference())
                    .completedAt(now)
                    .build();

            TransactionEntity savedTransaction = transactionRepository.save(transaction);

            return toResponse(savedTransaction);
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

            Page<TransactionEntity> transactions = transactionRepository.findHistoryByWallet(wallet, pageRequest);

            return PageResponse.builder()
                    .content(Collections.singletonList(transactions.getContent()
                            .stream()
                            .map(this::toResponse)
                            .toList()))
                    .page(transactions.getNumber())
                    .size(transactions.getSize())
                    .totalElements(transactions.getTotalElements())
                    .totalPages(transactions.getTotalPages())
                    .first(transactions.isFirst())
                    .last(transactions.isLast())
                    .build();
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

        private BigDecimal safe(BigDecimal value) {
            return value != null ? value : BigDecimal.ZERO;
        }

        private String generateUniqueReference() {
            String reference;

            do {
                reference = "P2P-"
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

        private int normalizePage(int page) {
            return Math.max(page, 0);
        }

        private int normalizeSize(int size) {
            if (size <= 0) {
                return DEFAULT_PAGE_SIZE;
            }

            return Math.min(size, MAX_PAGE_SIZE);
        }

        private String fullName(UserEntity user) {
            return (user.getFirstName() + " " + user.getLastName()).trim();
        }

        private record WalletPair(
                WalletEntity sourceWallet,
                WalletEntity targetWallet
        ) {
        }
    }