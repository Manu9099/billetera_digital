package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.P2PRequest;
import com.yapeseguro.api.dto.response.PageResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse sendP2P(P2PRequest request, String username) {
        UserEntity sender = getUserByUsername(username);

        UserEntity recipient = userRepository.findById(request.getRecipientUserId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario destinatario no encontrado"));

        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("No puedes transferirte dinero a ti mismo");
        }

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }

        WalletEntity sourceWalletRef = walletRepository.findById(request.getSourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Billetera origen no encontrada"));

        if (!sourceWalletRef.getUser().getId().equals(sender.getId())) {
            throw new IllegalArgumentException("No tienes permiso para usar esta billetera origen");
        }

        if (sourceWalletRef.getWalletType() != WalletEntity.WalletType.PERSONAL) {
            throw new IllegalArgumentException("Las transferencias P2P solo pueden salir de una billetera personal");
        }

        WalletEntity recipientWalletRef = walletRepository
                .findByUserAndWalletType(recipient, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("El usuario destinatario no tiene billetera personal"));

        WalletEntity sourceWallet;
        WalletEntity recipientWallet;

        UUID sourceWalletId = sourceWalletRef.getId();
        UUID recipientWalletId = recipientWalletRef.getId();

        if (sourceWalletId.compareTo(recipientWalletId) <= 0) {
            WalletEntity firstLocked = lockWallet(sourceWalletId);
            WalletEntity secondLocked = lockWallet(recipientWalletId);

            sourceWallet = firstLocked.getId().equals(sourceWalletId) ? firstLocked : secondLocked;
            recipientWallet = firstLocked.getId().equals(recipientWalletId) ? firstLocked : secondLocked;
        } else {
            WalletEntity firstLocked = lockWallet(recipientWalletId);
            WalletEntity secondLocked = lockWallet(sourceWalletId);

            sourceWallet = firstLocked.getId().equals(sourceWalletId) ? firstLocked : secondLocked;
            recipientWallet = firstLocked.getId().equals(recipientWalletId) ? firstLocked : secondLocked;
        }

        validateP2PWallets(sourceWallet, recipientWallet);

        if (!sourceWallet.getCurrency().equals(recipientWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        BigDecimal sourceBalance = safe(sourceWallet.getBalance());
        BigDecimal sourceAvailableBalance = safe(sourceWallet.getAvailableBalance());

        if (sourceAvailableBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        OffsetDateTime now = OffsetDateTime.now();

        sourceWallet.setBalance(sourceBalance.subtract(amount));
        sourceWallet.setAvailableBalance(sourceAvailableBalance.subtract(amount));
        sourceWallet.setMonthlyExpenses(safe(sourceWallet.getMonthlyExpenses()).add(amount));
        sourceWallet.setDailyTxCount(sourceWallet.getDailyTxCount() + 1);
        sourceWallet.setLastTransactionAt(now);

        recipientWallet.setBalance(safe(recipientWallet.getBalance()).add(amount));
        recipientWallet.setAvailableBalance(safe(recipientWallet.getAvailableBalance()).add(amount));
        recipientWallet.setMonthlyRevenue(safe(recipientWallet.getMonthlyRevenue()).add(amount));
        recipientWallet.setDailyTxCount(recipientWallet.getDailyTxCount() + 1);
        recipientWallet.setLastTransactionAt(now);

        walletRepository.saveAll(List.of(sourceWallet, recipientWallet));

        TransactionEntity transaction = TransactionEntity.builder()
                .walletFrom(sourceWallet)
                .walletTo(recipientWallet)
                .amount(amount)
                .currency(sourceWallet.getCurrency())
                .type(TransactionEntity.TxType.P2P)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .concept(request.getConcept())
                .description("Transferencia P2P")
                .reference(generateReference("P2P"))
                .notes(request.getNotes())
                .completedAt(now)
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getWalletTransactions(
            UUID walletId,
            String username,
            int page,
            int size
    ) {
        UserEntity user = getUserByUsername(username);

        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada"));

        if (!wallet.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("No tienes permiso para ver esta billetera");
        }

        Pageable pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<TransactionResponse> transactions = transactionRepository
                .findHistoryByWallet(wallet, pageable)
                .map(this::toResponse);

        return toPageResponse(transactions);
    }

    private WalletEntity lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada"));
    }

    private void validateP2PWallets(WalletEntity sourceWallet, WalletEntity recipientWallet) {
        if (!sourceWallet.isActive()) {
            throw new IllegalArgumentException("La billetera origen no está activa");
        }

        if (!recipientWallet.isActive()) {
            throw new IllegalArgumentException("La billetera destinataria no está activa");
        }

        if (sourceWallet.getWalletType() != WalletEntity.WalletType.PERSONAL) {
            throw new IllegalArgumentException("La billetera origen debe ser personal");
        }

        if (recipientWallet.getWalletType() != WalletEntity.WalletType.PERSONAL) {
            throw new IllegalArgumentException("La billetera destino debe ser personal");
        }
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
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
                .concept(transaction.getConcept())
                .description(transaction.getDescription())
                .reference(transaction.getReference())
                .notes(transaction.getNotes())
                .holdExpiresAt(transaction.getHoldExpiresAt())
                .completedAt(transaction.getCompletedAt())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private PageResponse<TransactionResponse> toPageResponse(Page<TransactionResponse> page) {
        return PageResponse.<TransactionResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private String generateReference(String prefix) {
        String reference;

        do {
            reference = prefix
                    + "-"
                    + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (transactionRepository.existsByReference(reference));

        return reference;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String fullName(UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}