package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateDisputeRequest;
import com.yapeseguro.api.dto.response.DisputeResponse;
import com.yapeseguro.infrastructure.persistence.entities.DisputeEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.DisputeRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    private static final int DEFAULT_DISPUTE_EXPIRATION_DAYS = 7;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final DisputeRepository disputeRepository;

    @Transactional
    public DisputeResponse openMarketplaceDispute(
            UUID transactionId,
            CreateDisputeRequest request,
            String username
    ) {
        UserEntity buyer = getUserByUsername(username);

        TransactionEntity transaction = transactionRepository.findDetailedByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateCanOpenMarketplaceDispute(transaction, buyer);

        if (disputeRepository.existsByTransaction(transaction)) {
            throw new IllegalArgumentException("Esta transacción ya tiene una disputa registrada");
        }

        OffsetDateTime now = OffsetDateTime.now();

        UserEntity seller = transaction.getWalletTo().getUser();

        DisputeEntity dispute = DisputeEntity.builder()
                .transaction(transaction)
                .createdByUser(buyer)
                .respondentUser(seller)
                .reason(request.getReason())
                .description(normalizeRequired(request.getDescription(), "La descripción es obligatoria"))
                .disputedAmount(transaction.getAmount())
                .status(DisputeEntity.DisputeStatus.OPEN)
                .isMarketplaceDispute(true)
                .qrPhotoUrl(normalize(request.getQrPhotoUrl()))
                .chatTranscript(normalize(request.getChatTranscript()))
                .openedAt(now)
                .expiresAt(now.plusDays(DEFAULT_DISPUTE_EXPIRATION_DAYS))
                .build();

        DisputeEntity savedDispute = disputeRepository.save(dispute);

        transaction.setMarketplaceStatus(TransactionEntity.MpStatus.DISPUTED);
        transaction.setMarketplaceDispute(savedDispute);
        transaction.setNotes(appendNote(
                transaction.getNotes(),
                "Disputa abierta por el comprador."
        ));

        transactionRepository.save(transaction);

        return toResponse(savedDispute);
    }

    @Transactional(readOnly = true)
    public List<DisputeResponse> getMyDisputes(String username) {
        UserEntity user = getUserByUsername(username);

        return disputeRepository
                .findByCreatedByUserOrRespondentUserOrderByOpenedAtDesc(user, user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DisputeResponse getTransactionDispute(
            UUID transactionId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        DisputeEntity dispute = disputeRepository.findDetailedByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Disputa no encontrada"));

        validateDisputeBelongsToUser(dispute, user);

        return toResponse(dispute);
    }

    @Transactional
    public int resolveExpiredMarketplaceDisputesAutomatically() {
        OffsetDateTime now = OffsetDateTime.now();

        List<DisputeEntity> expiredDisputes = disputeRepository.findExpiredMarketplaceDisputesForUpdate(
                List.of(
                        DisputeEntity.DisputeStatus.OPEN,
                        DisputeEntity.DisputeStatus.EVIDENCE_REVIEW,
                        DisputeEntity.DisputeStatus.IN_RESOLUTION
                ),
                now
        );

        int resolved = 0;

        for (DisputeEntity dispute : expiredDisputes) {
            try {
                autoRefundExpiredDispute(dispute, now);
                resolved++;
            } catch (RuntimeException ex) {
                log.warn(
                        "Could not auto-resolve dispute {}: {}",
                        dispute.getId(),
                        ex.getMessage()
                );
            }
        }

        return resolved;
    }

    private void autoRefundExpiredDispute(
            DisputeEntity dispute,
            OffsetDateTime now
    ) {
        TransactionEntity transaction = dispute.getTransaction();

        if (transaction.getType() != TransactionEntity.TxType.MARKETPLACE) {
            return;
        }

        if (transaction.getStatus() != TransactionEntity.TxStatus.HELD
                || transaction.getMarketplaceStatus() != TransactionEntity.MpStatus.DISPUTED) {
            return;
        }

        BigDecimal amount = transaction.getAmount();

        WalletEntity buyerWallet = walletRepository.findByIdForUpdate(transaction.getWalletFrom().getId())
                .orElseThrow(() -> new IllegalArgumentException("Billetera del comprador no encontrada"));

        WalletEntity sellerBusinessWallet = walletRepository.findByIdForUpdate(transaction.getWalletTo().getId())
                .orElseThrow(() -> new IllegalArgumentException("Billetera del vendedor no encontrada"));

        if (safe(sellerBusinessWallet.getHoldAmount()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Hold insuficiente para reembolsar disputa");
        }

        sellerBusinessWallet.setHoldAmount(safe(sellerBusinessWallet.getHoldAmount()).subtract(amount));

        buyerWallet.setBalance(safe(buyerWallet.getBalance()).add(amount));
        buyerWallet.setAvailableBalance(safe(buyerWallet.getAvailableBalance()).add(amount));
        buyerWallet.setMonthlyRevenue(safe(buyerWallet.getMonthlyRevenue()).add(amount));

        buyerWallet.setLastTransactionAt(now);
        sellerBusinessWallet.setLastTransactionAt(now);

        transaction.setStatus(TransactionEntity.TxStatus.CANCELLED);
        transaction.setMarketplaceStatus(TransactionEntity.MpStatus.DISPUTED);
        transaction.setCompletedAt(now);
        transaction.setNotes(appendNote(
                transaction.getNotes(),
                "Disputa vencida: reembolso automático al comprador."
        ));

        dispute.setStatus(DisputeEntity.DisputeStatus.RESOLVED);
        dispute.setResolution(DisputeEntity.DisputeResolution.REFUND);
        dispute.setRefundAmount(amount);
        dispute.setResolvedAt(now);
        dispute.setClosedAt(now);
        dispute.setResolutionNotes("Resolución automática: disputa vencida sin resolución manual. Se reembolsó el monto al comprador.");

        walletRepository.saveAll(List.of(buyerWallet, sellerBusinessWallet));
        transactionRepository.save(transaction);
        disputeRepository.save(dispute);
    }

    private void validateCanOpenMarketplaceDispute(
            TransactionEntity transaction,
            UserEntity buyer
    ) {
        if (transaction.getType() != TransactionEntity.TxType.MARKETPLACE) {
            throw new IllegalArgumentException("Solo se pueden disputar transacciones marketplace");
        }

        if (!transaction.getWalletFrom().getUser().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("Solo el comprador puede abrir una disputa");
        }

        if (transaction.getStatus() != TransactionEntity.TxStatus.HELD
                || transaction.getMarketplaceStatus() != TransactionEntity.MpStatus.HELD) {
            throw new IllegalArgumentException("Solo se pueden disputar pagos retenidos");
        }
    }

    private void validateDisputeBelongsToUser(
            DisputeEntity dispute,
            UserEntity user
    ) {
        UUID userId = user.getId();

        UUID createdByUserId = dispute.getCreatedByUser().getId();
        UUID respondentUserId = dispute.getRespondentUser().getId();

        if (!createdByUserId.equals(userId) && !respondentUserId.equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para ver esta disputa");
        }
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private DisputeResponse toResponse(DisputeEntity dispute) {
        TransactionEntity transaction = dispute.getTransaction();

        return DisputeResponse.builder()
                .id(dispute.getId())
                .transactionId(transaction.getId())
                .createdByUserId(dispute.getCreatedByUser().getId())
                .createdByName(fullName(dispute.getCreatedByUser()))
                .respondentUserId(dispute.getRespondentUser().getId())
                .respondentName(fullName(dispute.getRespondentUser()))
                .reason(dispute.getReason().name())
                .description(dispute.getDescription())
                .disputedAmount(dispute.getDisputedAmount())
                .status(dispute.getStatus().name())
                .marketplaceDispute(dispute.isMarketplaceDispute())
                .recipientPhone(dispute.getRecipientPhone())
                .qrPhotoUrl(dispute.getQrPhotoUrl())
                .chatTranscript(dispute.getChatTranscript())
                .openedAt(dispute.getOpenedAt())
                .evidenceSubmittedAt(dispute.getEvidenceSubmittedAt())
                .inResolutionAt(dispute.getInResolutionAt())
                .resolvedAt(dispute.getResolvedAt())
                .closedAt(dispute.getClosedAt())
                .expiresAt(dispute.getExpiresAt())
                .resolution(dispute.getResolution() != null ? dispute.getResolution().name() : null)
                .refundAmount(dispute.getRefundAmount())
                .resolutionNotes(dispute.getResolutionNotes())
                .transactionStatus(transaction.getStatus().name())
                .transactionMarketplaceStatus(transaction.getMarketplaceStatus().name())
                .transactionReference(transaction.getReference())
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .build();
    }

    private String fullName(UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
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
}