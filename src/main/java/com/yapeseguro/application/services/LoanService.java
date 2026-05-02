package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateLoanRequest;
import com.yapeseguro.api.dto.request.RepayLoanRequest;
import com.yapeseguro.api.dto.response.LoanPreviewResponse;
import com.yapeseguro.api.dto.response.LoanRepaymentResponse;
import com.yapeseguro.api.dto.response.LoanResponse;
import com.yapeseguro.infrastructure.persistence.entities.LoanEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.LoanRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LoanRepository loanRepository;
    private final ReceiptService receiptService;

    @Transactional(readOnly = true)
    public LoanPreviewResponse previewLoan(
            CreateLoanRequest request,
            String username
    ) {
        UserEntity lender = getUserByUsername(username);

        UserEntity borrower = userRepository.findById(request.getBorrowerUserId())
                .orElseThrow(() -> new IllegalArgumentException("Prestatario no encontrado"));

        if (lender.getId().equals(borrower.getId())) {
            throw new IllegalArgumentException("No puedes prestarte dinero a ti mismo");
        }

        BigDecimal originalAmount = validatePositive(
                request.getOriginalAmount(),
                "El monto original debe ser mayor a cero"
        );

        BigDecimal interestRate = validatePercentage(request.getInterestRate());

        BigDecimal lateFeePerDay = validateNonNegative(
                request.getLateFeePerDay(),
                "La mora diaria no puede ser negativa"
        );

        BigDecimal interestAmount = calculateInterestAmount(originalAmount, interestRate);
        BigDecimal totalAmountToReturn = originalAmount.add(interestAmount).setScale(2, RoundingMode.HALF_UP);

        return LoanPreviewResponse.builder()
                .borrowerUserId(borrower.getId())
                .borrowerName(fullName(borrower))
                .originalAmount(originalAmount)
                .interestRate(interestRate)
                .interestAmount(interestAmount)
                .totalAmountToReturn(totalAmountToReturn)
                .lateFeePerDay(lateFeePerDay)
                .dueDate(request.getDueDate())
                .transparencySummary(buildTransparencySummary(
                        originalAmount,
                        interestRate,
                        interestAmount,
                        totalAmountToReturn,
                        lateFeePerDay,
                        request.getDueDate()
                ))
                .build();
    }

    @Transactional
    public LoanResponse createLoan(
            CreateLoanRequest request,
            String username
    ) {
        UserEntity lender = getUserByUsername(username);

        UserEntity borrower = userRepository.findById(request.getBorrowerUserId())
                .orElseThrow(() -> new IllegalArgumentException("Prestatario no encontrado"));

        if (lender.getId().equals(borrower.getId())) {
            throw new IllegalArgumentException("No puedes prestarte dinero a ti mismo");
        }

        BigDecimal originalAmount = validatePositive(
                request.getOriginalAmount(),
                "El monto original debe ser mayor a cero"
        );

        BigDecimal interestRate = validatePercentage(request.getInterestRate());

        BigDecimal lateFeePerDay = validateNonNegative(
                request.getLateFeePerDay(),
                "La mora diaria no puede ser negativa"
        );

        BigDecimal interestAmount = calculateInterestAmount(originalAmount, interestRate);
        BigDecimal totalAmountToReturn = originalAmount.add(interestAmount).setScale(2, RoundingMode.HALF_UP);

        WalletEntity lenderWalletRef = getPersonalWallet(lender);
        WalletEntity borrowerWalletRef = getPersonalWallet(borrower);

        WalletPair lockedWallets = lockWalletsInStableOrder(
                lenderWalletRef.getId(),
                borrowerWalletRef.getId()
        );

        WalletEntity lenderWallet = lockedWallets.sourceWallet();
        WalletEntity borrowerWallet = lockedWallets.targetWallet();

        validateWalletIsActive(lenderWallet, "La billetera del prestamista no está activa");
        validateWalletIsActive(borrowerWallet, "La billetera del prestatario no está activa");

        if (!lenderWallet.getCurrency().equals(borrowerWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        if (safe(lenderWallet.getAvailableBalance()).compareTo(originalAmount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente para desembolsar el préstamo");
        }

        OffsetDateTime now = OffsetDateTime.now();

        applyDebit(lenderWallet, originalAmount);
        applyCredit(borrowerWallet, originalAmount);

        lenderWallet.setLastTransactionAt(now);
        borrowerWallet.setLastTransactionAt(now);

        walletRepository.saveAll(List.of(lenderWallet, borrowerWallet));

        TransactionEntity disbursementTransaction = TransactionEntity.builder()
                .walletFrom(lenderWallet)
                .walletTo(borrowerWallet)
                .amount(originalAmount)
                .currency(lenderWallet.getCurrency())
                .type(TransactionEntity.TxType.P2P)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .description("Desembolso de préstamo transparente")
                .concept("Préstamo")
                .reference(generateUniqueReference("LOAN"))
                .notes(buildLoanCreationNotes(
                        totalAmountToReturn,
                        interestRate,
                        lateFeePerDay,
                        request.getNotes()
                ))
                .completedAt(now)
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(disbursementTransaction);

        receiptService.generateReceiptForTransaction(savedTransaction.getId());

        LoanEntity loan = LoanEntity.builder()
                .borrowerUser(borrower)
                .lenderUser(lender)
                .transaction(savedTransaction)
                .originalAmount(originalAmount)
                .remainingBalance(totalAmountToReturn)
                .interestRate(interestRate)
                .totalAmountToReturn(totalAmountToReturn)
                .lateFeePerDay(lateFeePerDay)
                .loanStatus(LoanEntity.LoanStatus.ACTIVE)
                .loanDate(now)
                .dueDate(request.getDueDate())
                .notes(normalize(request.getNotes()))
                .build();

        return toResponse(loanRepository.save(loan));
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getMyLoans(String username) {
        UserEntity user = getUserByUsername(username);

        return loanRepository.findByBorrowerUserOrLenderUserOrderByCreatedAtDesc(user, user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoanById(
            UUID loanId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        LoanEntity loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado"));

        validateLoanBelongsToUser(loan, user);

        return toResponse(loan);
    }

    @Transactional
    public LoanRepaymentResponse repayLoan(
            UUID loanId,
            RepayLoanRequest request,
            String username
    ) {
        UserEntity borrower = getUserByUsername(username);

        LoanEntity loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado"));

        validateBorrower(loan, borrower);

        if (loan.getLoanStatus() != LoanEntity.LoanStatus.ACTIVE
                && loan.getLoanStatus() != LoanEntity.LoanStatus.DEFAULT) {
            throw new IllegalArgumentException("El préstamo no está activo para pago");
        }

        BigDecimal currentDebt = calculateCurrentDebt(loan, OffsetDateTime.now());

        BigDecimal amount = request.getAmount() != null
                ? validatePositive(request.getAmount(), "El monto de pago debe ser mayor a cero")
                : currentDebt;

        if (amount.compareTo(currentDebt) > 0) {
            amount = currentDebt;
        }

        WalletEntity borrowerWalletRef = getPersonalWallet(loan.getBorrowerUser());
        WalletEntity lenderWalletRef = getPersonalWallet(loan.getLenderUser());

        WalletPair lockedWallets = lockWalletsInStableOrder(
                borrowerWalletRef.getId(),
                lenderWalletRef.getId()
        );

        WalletEntity borrowerWallet = lockedWallets.sourceWallet();
        WalletEntity lenderWallet = lockedWallets.targetWallet();

        validateWalletIsActive(borrowerWallet, "La billetera del prestatario no está activa");
        validateWalletIsActive(lenderWallet, "La billetera del prestamista no está activa");

        if (!borrowerWallet.getCurrency().equals(lenderWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        if (safe(borrowerWallet.getAvailableBalance()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente para pagar el préstamo");
        }

        OffsetDateTime now = OffsetDateTime.now();

        applyDebit(borrowerWallet, amount);
        applyCredit(lenderWallet, amount);

        borrowerWallet.setLastTransactionAt(now);
        lenderWallet.setLastTransactionAt(now);

        walletRepository.saveAll(List.of(borrowerWallet, lenderWallet));

        BigDecimal newRemainingBalance = currentDebt.subtract(amount).setScale(2, RoundingMode.HALF_UP);

        if (newRemainingBalance.compareTo(BigDecimal.ZERO) < 0) {
            newRemainingBalance = BigDecimal.ZERO;
        }

        TransactionEntity repaymentTransaction = TransactionEntity.builder()
                .walletFrom(borrowerWallet)
                .walletTo(lenderWallet)
                .amount(amount)
                .currency(borrowerWallet.getCurrency())
                .type(TransactionEntity.TxType.P2P)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .description("Pago de préstamo transparente")
                .concept("Pago de préstamo")
                .reference(generateUniqueReference("LPAY"))
                .notes(buildLoanRepaymentNotes(loan, request.getNotes()))
                .completedAt(now)
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(repaymentTransaction);

        receiptService.generateReceiptForTransaction(savedTransaction.getId());

        loan.setRemainingBalance(newRemainingBalance);

        if (newRemainingBalance.compareTo(BigDecimal.ZERO) == 0) {
            loan.setLoanStatus(LoanEntity.LoanStatus.COMPLETED);
            loan.setCompletedDate(now);
        } else if (isOverdue(loan, now)) {
            loan.setLoanStatus(LoanEntity.LoanStatus.DEFAULT);
        } else {
            loan.setLoanStatus(LoanEntity.LoanStatus.ACTIVE);
        }

        LoanEntity savedLoan = loanRepository.save(loan);

        return LoanRepaymentResponse.builder()
                .loanId(savedLoan.getId())
                .transactionId(savedTransaction.getId())
                .transactionReference(savedTransaction.getReference())
                .amountPaid(amount)
                .remainingBalance(savedLoan.getRemainingBalance())
                .loanStatus(savedLoan.getLoanStatus().name())
                .completedDate(savedLoan.getCompletedDate())
                .build();
    }

    @Transactional
    public LoanResponse cancelLoan(
            UUID loanId,
            String username
    ) {
        UserEntity lender = getUserByUsername(username);

        LoanEntity loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado"));

        if (!loan.getLenderUser().getId().equals(lender.getId())) {
            throw new IllegalArgumentException("Solo el prestamista puede cancelar el préstamo");
        }

        if (loan.getLoanStatus() == LoanEntity.LoanStatus.COMPLETED) {
            throw new IllegalArgumentException("No puedes cancelar un préstamo completado");
        }

        if (loan.getLoanStatus() == LoanEntity.LoanStatus.CANCELLED) {
            return toResponse(loan);
        }

        loan.setLoanStatus(LoanEntity.LoanStatus.CANCELLED);

        return toResponse(loanRepository.save(loan));
    }

    private BigDecimal calculateCurrentDebt(
            LoanEntity loan,
            OffsetDateTime now
    ) {
        BigDecimal baseRemaining = safe(loan.getRemainingBalance());

        if (!isOverdue(loan, now)) {
            return baseRemaining.setScale(2, RoundingMode.HALF_UP);
        }

        long overdueDays = ChronoUnit.DAYS.between(
                loan.getDueDate().toLocalDate(),
                now.toLocalDate()
        );

        if (overdueDays <= 0) {
            return baseRemaining.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal lateFees = safe(loan.getLateFeePerDay())
                .multiply(BigDecimal.valueOf(overdueDays))
                .setScale(2, RoundingMode.HALF_UP);

        return baseRemaining.add(lateFees).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isOverdue(
            LoanEntity loan,
            OffsetDateTime now
    ) {
        return loan.getDueDate() != null
                && now.isAfter(loan.getDueDate())
                && loan.getLoanStatus() != LoanEntity.LoanStatus.COMPLETED
                && loan.getLoanStatus() != LoanEntity.LoanStatus.CANCELLED;
    }

    private BigDecimal calculateInterestAmount(
            BigDecimal originalAmount,
            BigDecimal interestRate
    ) {
        return originalAmount
                .multiply(interestRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal validatePositive(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal validateNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(message);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal validatePercentage(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("La tasa de interés debe estar entre 0 y 100");
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildTransparencySummary(
            BigDecimal originalAmount,
            BigDecimal interestRate,
            BigDecimal interestAmount,
            BigDecimal totalAmountToReturn,
            BigDecimal lateFeePerDay,
            OffsetDateTime dueDate
    ) {
        String dueDateText = dueDate != null ? dueDate.toString() : "sin fecha de vencimiento";

        return "Préstamo de PEN "
                + originalAmount
                + " con tasa de "
                + interestRate
                + "%. Interés: PEN "
                + interestAmount
                + ". Total a devolver: PEN "
                + totalAmountToReturn
                + ". Mora diaria: PEN "
                + lateFeePerDay
                + ". Vence: "
                + dueDateText
                + ".";
    }

    private String buildLoanCreationNotes(
            BigDecimal totalAmountToReturn,
            BigDecimal interestRate,
            BigDecimal lateFeePerDay,
            String notes
    ) {
        String base = "TOTAL_RETURN=" + totalAmountToReturn
                + "; INTEREST_RATE=" + interestRate
                + "; LATE_FEE_PER_DAY=" + lateFeePerDay;

        String normalizedNotes = normalize(notes);

        return normalizedNotes != null ? base + "; " + normalizedNotes : base;
    }

    private String buildLoanRepaymentNotes(
            LoanEntity loan,
            String notes
    ) {
        String base = "LOAN_ID=" + loan.getId();

        String normalizedNotes = normalize(notes);

        return normalizedNotes != null ? base + "; " + normalizedNotes : base;
    }

    private LoanResponse toResponse(LoanEntity loan) {
        BigDecimal currentDebt = calculateCurrentDebt(loan, OffsetDateTime.now());

        return LoanResponse.builder()
                .id(loan.getId())
                .borrowerUserId(loan.getBorrowerUser().getId())
                .borrowerName(fullName(loan.getBorrowerUser()))
                .lenderUserId(loan.getLenderUser().getId())
                .lenderName(fullName(loan.getLenderUser()))
                .transactionId(loan.getTransaction() != null ? loan.getTransaction().getId() : null)
                .originalAmount(loan.getOriginalAmount())
                .remainingBalance(loan.getRemainingBalance())
                .currentDebt(currentDebt)
                .interestRate(loan.getInterestRate())
                .totalAmountToReturn(loan.getTotalAmountToReturn())
                .lateFeePerDay(loan.getLateFeePerDay())
                .loanStatus(loan.getLoanStatus().name())
                .loanDate(loan.getLoanDate())
                .dueDate(loan.getDueDate())
                .completedDate(loan.getCompletedDate())
                .overdue(isOverdue(loan, OffsetDateTime.now()))
                .notes(loan.getNotes())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                .build();
    }

    private void validateLoanBelongsToUser(
            LoanEntity loan,
            UserEntity user
    ) {
        UUID userId = user.getId();

        if (!loan.getBorrowerUser().getId().equals(userId)
                && !loan.getLenderUser().getId().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para ver este préstamo");
        }
    }

    private void validateBorrower(
            LoanEntity loan,
            UserEntity user
    ) {
        if (!loan.getBorrowerUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Solo el prestatario puede pagar este préstamo");
        }
    }

    private WalletEntity getPersonalWallet(UserEntity user) {
        return walletRepository
                .findByUserAndWalletType(user, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene billetera personal"));
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

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
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

    private record WalletPair(
            WalletEntity sourceWallet,
            WalletEntity targetWallet
    ) {
    }
}