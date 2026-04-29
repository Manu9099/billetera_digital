package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.P2PRequest;
import com.yapeseguro.api.dto.response.TransactionResponse;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yapeseguro.api.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

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

        WalletEntity sourceWallet = walletRepository.findById(request.getSourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Billetera origen no encontrada"));

        validateSourceWalletBelongsToSender(sourceWallet, sender);
        validateWalletIsActive(sourceWallet);

        WalletEntity targetWallet = walletRepository
                .findByUserAndWalletType(recipient, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("El receptor no tiene billetera personal"));

        validateWalletIsActive(targetWallet);

        BigDecimal amount = request.getAmount();

        if (safe(sourceWallet.getAvailableBalance()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        applyDebit(sourceWallet, amount);
        applyCredit(targetWallet, amount);

        OffsetDateTime now = OffsetDateTime.now();

        sourceWallet.setLastTransactionAt(now);
        targetWallet.setLastTransactionAt(now);

        walletRepository.save(sourceWallet);
        walletRepository.save(targetWallet);

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

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private void validateSourceWalletBelongsToSender(WalletEntity wallet, UserEntity sender) {
        if (!wallet.getUser().getId().equals(sender.getId())) {
            throw new IllegalArgumentException("No tienes permiso para usar esta billetera");
        }
    }

    private void validateWalletIsActive(WalletEntity wallet) {
        if (!wallet.isActive()) {
            throw new IllegalArgumentException("La billetera no está activa");
        }
    }

    private void applyDebit(WalletEntity wallet, BigDecimal amount) {
        wallet.setBalance(safe(wallet.getBalance()).subtract(amount));
        wallet.setAvailableBalance(safe(wallet.getAvailableBalance()).subtract(amount));
        wallet.setMonthlyExpenses(safe(wallet.getMonthlyExpenses()).add(amount));
        wallet.setDailyTxCount(wallet.getDailyTxCount() + 1);
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

        validateSourceWalletBelongsToSender(wallet, user);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);

        PageRequest pageRequest = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<TransactionEntity> transactions = transactionRepository
                .findByWalletFromOrWalletTo(wallet, wallet, pageRequest);

        return PageResponse.<TransactionResponse>builder()
                .content(transactions.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList())
                .page(transactions.getNumber())
                .size(transactions.getSize())
                .totalElements(transactions.getTotalElements())
                .totalPages(transactions.getTotalPages())
                .first(transactions.isFirst())
                .last(transactions.isLast())
                .build();
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
            reference = "P2P-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        } while (transactionRepository.existsByReference(reference));

        return reference;
    }

    private TransactionResponse toResponse(TransactionEntity transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .walletFromId(transaction.getWalletFrom().getId())
                .walletToId(transaction.getWalletTo().getId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType().name())
                .status(transaction.getStatus().name())
                .marketplaceStatus(transaction.getMarketplaceStatus().name())
                .description(transaction.getDescription())
                .concept(transaction.getConcept())
                .reference(transaction.getReference())
                .notes(transaction.getNotes())
                .completedAt(transaction.getCompletedAt())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}