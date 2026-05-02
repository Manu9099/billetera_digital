package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateScheduledPaymentRequest;
import com.yapeseguro.api.dto.response.ScheduledPaymentResponse;
import com.yapeseguro.infrastructure.persistence.entities.NotificationEntity;
import com.yapeseguro.infrastructure.persistence.entities.ScheduledPaymentEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.NotificationRepository;
import com.yapeseguro.infrastructure.persistence.repositories.ScheduledPaymentRepository;
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
public class ScheduledPaymentService {

    private static final int MAX_FAILURE_RETRIES = 3;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final NotificationRepository notificationRepository;
    private final ReceiptService receiptService;

    @Transactional
    public ScheduledPaymentResponse createScheduledPayment(
            CreateScheduledPaymentRequest request,
            String username
    ) {
        UserEntity owner = getUserByUsername(username);

        WalletEntity sourceWallet = walletRepository
                .findByUserAndWalletType(owner, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("No tienes billetera personal"));

        if (!sourceWallet.isActive()) {
            throw new IllegalArgumentException("Tu billetera personal no está activa");
        }

        UserEntity recipientUser = null;
        WalletEntity targetWallet = null;

        if (request.getRecipientUserId() != null) {
            recipientUser = userRepository.findById(request.getRecipientUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario receptor no encontrado"));

            if (recipientUser.getId().equals(owner.getId())) {
                throw new IllegalArgumentException("No puedes programar un pago hacia ti mismo");
            }

            targetWallet = walletRepository
                    .findByUserAndWalletType(recipientUser, WalletEntity.WalletType.PERSONAL)
                    .orElseThrow(() -> new IllegalArgumentException("El receptor no tiene billetera personal"));
        }

        if (request.isAutoPayEnabled() && targetWallet == null) {
            throw new IllegalArgumentException("El autopago requiere un usuario receptor registrado");
        }

        ScheduledPaymentEntity.Frequency frequency = parseFrequency(request.getFrequency());

        ScheduledPaymentEntity scheduledPayment = ScheduledPaymentEntity.builder()
                .walletFrom(sourceWallet)
                .walletTo(targetWallet)
                .recipientUser(recipientUser)
                .recipientName(resolveRecipientName(request, recipientUser))
                .recipientPhone(normalize(request.getRecipientPhone()))
                .amount(request.getAmount())
                .currency(sourceWallet.getCurrency())
                .concept(normalizeRequired(request.getConcept(), "El concepto es obligatorio"))
                .description(normalize(request.getDescription()))
                .frequency(frequency)
                .dayOfMonth(toShort(request.getDayOfMonth()))
                .dayOfWeek(toShort(request.getDayOfWeek()))
                .nextPaymentDate(request.getNextPaymentDate())
                .startDate(OffsetDateTime.now())
                .endDate(request.getEndDate())
                .autoPayEnabled(request.isAutoPayEnabled())
                .failureRetryCount((short) 0)
                .timesExecuted(0)
                .notifyDaysInAdvance(toShort(request.getNotifyDaysInAdvance()))
                .status(ScheduledPaymentEntity.ScheduledStatus.ACTIVE)
                .build();

        return toResponse(scheduledPaymentRepository.save(scheduledPayment));
    }

    @Transactional(readOnly = true)
    public List<ScheduledPaymentResponse> getMyScheduledPayments(String username) {
        UserEntity user = getUserByUsername(username);

        return scheduledPaymentRepository
                .findByWalletFrom_UserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduledPaymentResponse getMyScheduledPayment(
            UUID scheduledPaymentId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        ScheduledPaymentEntity scheduledPayment = scheduledPaymentRepository
                .findByIdAndWalletFrom_User(scheduledPaymentId, user)
                .orElseThrow(() -> new IllegalArgumentException("Pago programado no encontrado"));

        return toResponse(scheduledPayment);
    }

    @Transactional
    public ScheduledPaymentResponse pauseScheduledPayment(
            UUID scheduledPaymentId,
            String username
    ) {
        ScheduledPaymentEntity scheduledPayment = getOwnedScheduledPaymentForUpdate(
                scheduledPaymentId,
                username
        );

        if (scheduledPayment.getStatus() != ScheduledPaymentEntity.ScheduledStatus.ACTIVE) {
            throw new IllegalArgumentException("Solo puedes pausar pagos activos");
        }

        scheduledPayment.setStatus(ScheduledPaymentEntity.ScheduledStatus.PAUSED);
        scheduledPayment.setPausedAt(OffsetDateTime.now());

        return toResponse(scheduledPaymentRepository.save(scheduledPayment));
    }

    @Transactional
    public ScheduledPaymentResponse resumeScheduledPayment(
            UUID scheduledPaymentId,
            String username
    ) {
        ScheduledPaymentEntity scheduledPayment = getOwnedScheduledPaymentForUpdate(
                scheduledPaymentId,
                username
        );

        if (scheduledPayment.getStatus() != ScheduledPaymentEntity.ScheduledStatus.PAUSED) {
            throw new IllegalArgumentException("Solo puedes reanudar pagos pausados");
        }

        scheduledPayment.setStatus(ScheduledPaymentEntity.ScheduledStatus.ACTIVE);
        scheduledPayment.setPausedAt(null);

        return toResponse(scheduledPaymentRepository.save(scheduledPayment));
    }

    @Transactional
    public void cancelScheduledPayment(
            UUID scheduledPaymentId,
            String username
    ) {
        ScheduledPaymentEntity scheduledPayment = getOwnedScheduledPaymentForUpdate(
                scheduledPaymentId,
                username
        );

        if (scheduledPayment.getStatus() == ScheduledPaymentEntity.ScheduledStatus.CANCELLED) {
            return;
        }

        scheduledPayment.setStatus(ScheduledPaymentEntity.ScheduledStatus.CANCELLED);

        scheduledPaymentRepository.save(scheduledPayment);
    }

    @Transactional
    public ScheduledPaymentResponse payNow(
            UUID scheduledPaymentId,
            String username
    ) {
        ScheduledPaymentEntity scheduledPayment = getOwnedScheduledPaymentForUpdate(
                scheduledPaymentId,
                username
        );

        if (scheduledPayment.getStatus() != ScheduledPaymentEntity.ScheduledStatus.ACTIVE) {
            throw new IllegalArgumentException("El pago programado no está activo");
        }

        executeScheduledPayment(scheduledPayment, OffsetDateTime.now(), true);

        return toResponse(scheduledPaymentRepository.save(scheduledPayment));
    }

    @Transactional
    public int processDueAutoPayments() {
        OffsetDateTime now = OffsetDateTime.now();

        List<ScheduledPaymentEntity> duePayments = scheduledPaymentRepository.findDueAutoPayForUpdate(
                ScheduledPaymentEntity.ScheduledStatus.ACTIVE,
                now
        );

        int processed = 0;

        for (ScheduledPaymentEntity scheduledPayment : duePayments) {
            try {
                executeScheduledPayment(scheduledPayment, now, false);
                scheduledPaymentRepository.save(scheduledPayment);
                processed++;
            } catch (RuntimeException ex) {
                handleAutoPayFailure(scheduledPayment, ex.getMessage());
                scheduledPaymentRepository.save(scheduledPayment);

                log.warn(
                        "Scheduled payment {} failed: {}",
                        scheduledPayment.getId(),
                        ex.getMessage()
                );
            }
        }

        return processed;
    }

    @Transactional
    public int createUpcomingPaymentNotifications() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime maxDate = now.plusDays(30);

        List<ScheduledPaymentEntity> upcomingPayments = scheduledPaymentRepository.findActiveUpcoming(
                ScheduledPaymentEntity.ScheduledStatus.ACTIVE,
                now,
                maxDate
        );

        int created = 0;

        for (ScheduledPaymentEntity scheduledPayment : upcomingPayments) {
            if (!shouldNotify(scheduledPayment, now)) {
                continue;
            }

            UserEntity owner = scheduledPayment.getWalletFrom().getUser();

            boolean alreadyNotified = notificationRepository.existsByUserAndNotificationTypeAndRelatedEntityId(
                    owner,
                    NotificationEntity.NotificationType.SCHEDULED_PAYMENT,
                    scheduledPayment.getId()
            );

            if (alreadyNotified) {
                continue;
            }

            notificationRepository.save(
                    NotificationEntity.builder()
                            .user(owner)
                            .title("Pago programado próximo")
                            .message(buildUpcomingPaymentMessage(scheduledPayment))
                            .notificationType(NotificationEntity.NotificationType.SCHEDULED_PAYMENT)
                            .relatedEntityId(scheduledPayment.getId())
                            .read(false)
                            .sentVia(NotificationEntity.SentVia.IN_APP)
                            .expiresAt(scheduledPayment.getNextPaymentDate().plusDays(1))
                            .build()
            );

            created++;
        }

        return created;
    }

    private void executeScheduledPayment(
            ScheduledPaymentEntity scheduledPayment,
            OffsetDateTime now,
            boolean manualExecution
    ) {
        if (scheduledPayment.getWalletTo() == null) {
            throw new IllegalArgumentException("Este pago programado no tiene billetera destino");
        }

        WalletPair lockedWallets = lockWalletsInStableOrder(
                scheduledPayment.getWalletFrom().getId(),
                scheduledPayment.getWalletTo().getId()
        );

        WalletEntity sourceWallet = lockedWallets.sourceWallet();
        WalletEntity targetWallet = lockedWallets.targetWallet();

        validateWalletIsActive(sourceWallet, "La billetera origen no está activa");
        validateWalletIsActive(targetWallet, "La billetera destino no está activa");

        if (!sourceWallet.getCurrency().equals(targetWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        BigDecimal amount = scheduledPayment.getAmount();

        if (safe(sourceWallet.getAvailableBalance()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

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
                .type(TransactionEntity.TxType.SCHEDULED)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .description(resolveTransactionDescription(scheduledPayment, manualExecution))
                .concept(scheduledPayment.getConcept())
                .reference(generateUniqueReference("SCH"))
                .scheduledPaymentId(scheduledPayment.getId())
                .completedAt(now)
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        receiptService.generateReceiptForTransaction(savedTransaction.getId());

        scheduledPayment.setWalletFrom(sourceWallet);
        scheduledPayment.setWalletTo(targetWallet);
        scheduledPayment.setLastPaymentDate(now);
        scheduledPayment.setTimesExecuted(safeInt(scheduledPayment.getTimesExecuted()) + 1);
        scheduledPayment.setFailureRetryCount((short) 0);

        OffsetDateTime nextPaymentDate = calculateNextPaymentDate(scheduledPayment);

        if (nextPaymentDate == null
                || scheduledPayment.getEndDate() != null && nextPaymentDate.isAfter(scheduledPayment.getEndDate())) {
            scheduledPayment.setStatus(ScheduledPaymentEntity.ScheduledStatus.COMPLETED);
            scheduledPayment.setNextPaymentDate(now);
        } else {
            scheduledPayment.setNextPaymentDate(nextPaymentDate);
        }

        createSuccessfulPaymentNotification(scheduledPayment, savedTransaction);
    }

    private void handleAutoPayFailure(
            ScheduledPaymentEntity scheduledPayment,
            String reason
    ) {
        short retryCount = (short) (safeShort(scheduledPayment.getFailureRetryCount()) + 1);

        scheduledPayment.setFailureRetryCount(retryCount);

        if (retryCount >= MAX_FAILURE_RETRIES) {
            scheduledPayment.setStatus(ScheduledPaymentEntity.ScheduledStatus.PAUSED);
            scheduledPayment.setPausedAt(OffsetDateTime.now());
        }

        UserEntity owner = scheduledPayment.getWalletFrom().getUser();

        notificationRepository.save(
                NotificationEntity.builder()
                        .user(owner)
                        .title("No se pudo ejecutar tu pago programado")
                        .message("Pago a " + scheduledPayment.getRecipientName() + ": " + reason)
                        .notificationType(NotificationEntity.NotificationType.SCHEDULED_PAYMENT)
                        .relatedEntityId(scheduledPayment.getId())
                        .read(false)
                        .sentVia(NotificationEntity.SentVia.IN_APP)
                        .expiresAt(OffsetDateTime.now().plusDays(7))
                        .build()
        );
    }

    private void createSuccessfulPaymentNotification(
            ScheduledPaymentEntity scheduledPayment,
            TransactionEntity transaction
    ) {
        UserEntity owner = scheduledPayment.getWalletFrom().getUser();

        notificationRepository.save(
                NotificationEntity.builder()
                        .user(owner)
                        .title("Pago programado ejecutado")
                        .message("Se pagó " + transaction.getCurrency() + " " + transaction.getAmount()
                                + " a " + scheduledPayment.getRecipientName())
                        .notificationType(NotificationEntity.NotificationType.SCHEDULED_PAYMENT)
                        .relatedEntityId(scheduledPayment.getId())
                        .read(false)
                        .sentVia(NotificationEntity.SentVia.IN_APP)
                        .expiresAt(OffsetDateTime.now().plusDays(7))
                        .build()
        );
    }

    private boolean shouldNotify(
            ScheduledPaymentEntity scheduledPayment,
            OffsetDateTime now
    ) {
        OffsetDateTime notificationWindow = now.plusDays(safeShort(scheduledPayment.getNotifyDaysInAdvance()));

        return !scheduledPayment.getNextPaymentDate().isAfter(notificationWindow);
    }

    private String buildUpcomingPaymentMessage(ScheduledPaymentEntity scheduledPayment) {
        return "Tienes un pago programado de "
                + scheduledPayment.getCurrency()
                + " "
                + scheduledPayment.getAmount()
                + " para "
                + scheduledPayment.getRecipientName()
                + ". Fecha: "
                + scheduledPayment.getNextPaymentDate();
    }

    private ScheduledPaymentEntity getOwnedScheduledPaymentForUpdate(
            UUID scheduledPaymentId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        ScheduledPaymentEntity scheduledPayment = scheduledPaymentRepository
                .findByIdAndWalletFrom_User(scheduledPaymentId, user)
                .orElseThrow(() -> new IllegalArgumentException("Pago programado no encontrado"));

        if (scheduledPayment.getStatus() == ScheduledPaymentEntity.ScheduledStatus.CANCELLED) {
            throw new IllegalArgumentException("El pago programado está cancelado");
        }

        return scheduledPayment;
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

    private OffsetDateTime calculateNextPaymentDate(ScheduledPaymentEntity scheduledPayment) {
        OffsetDateTime current = scheduledPayment.getNextPaymentDate();

        return switch (scheduledPayment.getFrequency()) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case BIWEEKLY -> current.plusWeeks(2);
            case MONTHLY -> nextMonthlyDate(current, scheduledPayment.getDayOfMonth());
            case CUSTOM -> null;
        };
    }

    private OffsetDateTime nextMonthlyDate(
            OffsetDateTime current,
            Short dayOfMonth
    ) {
        OffsetDateTime next = current.plusMonths(1);

        if (dayOfMonth == null) {
            return next;
        }

        int safeDay = Math.min(dayOfMonth, 28);

        return next.withDayOfMonth(safeDay);
    }

    private ScheduledPaymentEntity.Frequency parseFrequency(String frequency) {
        try {
            return ScheduledPaymentEntity.Frequency.valueOf(frequency.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Frecuencia inválida");
        }
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private String resolveRecipientName(
            CreateScheduledPaymentRequest request,
            UserEntity recipientUser
    ) {
        String normalized = normalize(request.getRecipientName());

        if (normalized != null) {
            return normalized;
        }

        if (recipientUser != null) {
            return fullName(recipientUser);
        }

        throw new IllegalArgumentException("El nombre del receptor es obligatorio");
    }

    private String resolveTransactionDescription(
            ScheduledPaymentEntity scheduledPayment,
            boolean manualExecution
    ) {
        String base = normalize(scheduledPayment.getDescription());

        if (base != null) {
            return base;
        }

        return manualExecution
                ? "Pago programado ejecutado manualmente"
                : "Pago programado automático";
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

    private ScheduledPaymentResponse toResponse(ScheduledPaymentEntity scheduledPayment) {
        return ScheduledPaymentResponse.builder()
                .id(scheduledPayment.getId())
                .walletFromId(scheduledPayment.getWalletFrom().getId())
                .walletToId(scheduledPayment.getWalletTo() != null ? scheduledPayment.getWalletTo().getId() : null)
                .recipientUserId(scheduledPayment.getRecipientUser() != null ? scheduledPayment.getRecipientUser().getId() : null)
                .recipientName(scheduledPayment.getRecipientName())
                .recipientPhone(scheduledPayment.getRecipientPhone())
                .amount(scheduledPayment.getAmount())
                .currency(scheduledPayment.getCurrency())
                .concept(scheduledPayment.getConcept())
                .description(scheduledPayment.getDescription())
                .frequency(scheduledPayment.getFrequency().name())
                .dayOfMonth(toInteger(scheduledPayment.getDayOfMonth()))
                .dayOfWeek(toInteger(scheduledPayment.getDayOfWeek()))
                .nextPaymentDate(scheduledPayment.getNextPaymentDate())
                .lastPaymentDate(scheduledPayment.getLastPaymentDate())
                .startDate(scheduledPayment.getStartDate())
                .endDate(scheduledPayment.getEndDate())
                .autoPayEnabled(scheduledPayment.isAutoPayEnabled())
                .failureRetryCount(toInteger(scheduledPayment.getFailureRetryCount()))
                .timesExecuted(scheduledPayment.getTimesExecuted())
                .notifyDaysInAdvance(toInteger(scheduledPayment.getNotifyDaysInAdvance()))
                .status(scheduledPayment.getStatus().name())
                .pausedAt(scheduledPayment.getPausedAt())
                .createdAt(scheduledPayment.getCreatedAt())
                .updatedAt(scheduledPayment.getUpdatedAt())
                .build();
    }

    private Short toShort(Integer value) {
        return value != null ? value.shortValue() : null;
    }

    private Integer toInteger(Short value) {
        return value != null ? value.intValue() : null;
    }

    private short safeShort(Short value) {
        return value != null ? value : 0;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
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

    private record WalletPair(
            WalletEntity sourceWallet,
            WalletEntity targetWallet
    ) {
    }
}