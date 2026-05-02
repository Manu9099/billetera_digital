package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateLoanRequest;
import com.yapeseguro.api.dto.request.LoanRepaymentRequest;
import com.yapeseguro.api.dto.response.LoanRepaymentResponse;
import com.yapeseguro.api.dto.response.LoanResponse;
import com.yapeseguro.api.dto.response.LoanSummaryResponse;
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

    @Transactional
    public LoanResponse createLoan(
            CreateLoanRequest request,
            String username
    ) {
        UserEntity lender = getUserByUsername(username);

        UserEntity borrower = userRepository.findById(request.getBorrowerUserId())
                .orElseThrow(() -> new IllegalArgumentException("Prestatario no encontrado"));

        if (lender.getId().equals(borrower.getId())) {
            throw new IllegalArgumentException("No puedes crear un préstamo hacia ti mismo");
        }

        BigDecimal originalAmount = requirePositive(
                request.getOriginalAmount(),
                "El monto original debe ser mayor a cero"
        );

        BigDecimal interestRate = safe(request.getInterestRate());
        BigDecimal lateFeePerDay = safe(request.getLateFeePerDay());

        if (interestRate.compareTo(BigDecimal.ZERO) < 0 || interestRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("La tasa debe estar entre 0 y 100");
        }

        if (lateFeePerDay.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La mora diaria no puede ser negativa");
        }

        BigDecimal totalAmountToReturn = calculateTotalAmountToReturn(
                originalAmount,
                interestRate
        );

        OffsetDateTime now = OffsetDateTime.now();

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

        applyDebit(lenderWallet, originalAmount);
        applyCredit(borrowerWallet, originalAmount);

        lenderWallet.setLastTransactionAt(now);
        borrowerWallet.setLastTransactionAt(now);

        walletRepository.saveAll(List.of(lenderWallet, borrowerWallet));

        TransactionEntity transaction = TransactionEntity.builder()
                .walletFrom(lenderWallet)
                .walletTo(borrowerWallet)
                .amount(originalAmount)
                .currency(lenderWallet.getCurrency())
                .type(TransactionEntity.TxType.P2P)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .description("Desembolso de préstamo transparente")
                .concept("Préstamo transparente")
                .reference(generateUniqueReference("LOAN"))
                .notes(buildInitialLoanNote(interestRate, totalAmountToReturn, lateFeePerDay, request.getNotes()))
                .completedAt(now)
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(transaction);
        receiptService.generateReceiptForTransaction(savedTransaction.getId());

        LoanEntity loan = LoanEntity.builder()
                .borrowerUser(borrower)
                .lenderUser(lender)
                .transaction(savedTransaction