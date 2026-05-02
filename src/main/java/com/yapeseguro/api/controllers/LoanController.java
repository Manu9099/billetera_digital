package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateLoanRequest;
import com.yapeseguro.api.dto.request.RepayLoanRequest;
import com.yapeseguro.api.dto.response.LoanPreviewResponse;
import com.yapeseguro.api.dto.response.LoanRepaymentResponse;
import com.yapeseguro.api.dto.response.LoanResponse;
import com.yapeseguro.application.services.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    /**
     * POST /loans/preview
     * Calcula tasa, interés, total a devolver y mora sin crear el préstamo.
     */
    @PostMapping("/preview")
    public ResponseEntity<LoanPreviewResponse> previewLoan(
            @Valid @RequestBody CreateLoanRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                loanService.previewLoan(
                        request,
                        user.getUsername()
                )
        );
    }

    /**
     * POST /loans
     * Crea préstamo transparente y desembolsa dinero del prestamista al prestatario.
     */
    @PostMapping
    public ResponseEntity<LoanResponse> createLoan(
            @Valid @RequestBody CreateLoanRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.createLoan(
                        request,
                        user.getUsername()
                ));
    }

    /**
     * GET /loans
     * Lista préstamos donde soy prestamista o prestatario.
     */
    @GetMapping
    public ResponseEntity<List<LoanResponse>> getMyLoans(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                loanService.getMyLoans(user.getUsername())
        );
    }

    /**
     * GET /loans/{loanId}
     * Detalle de préstamo.
     */
    @GetMapping("/{loanId}")
    public ResponseEntity<LoanResponse> getLoanById(
            @PathVariable UUID loanId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                loanService.getLoanById(
                        loanId,
                        user.getUsername()
                )
        );
    }

    /**
     * POST /loans/{loanId}/repay
     * El prestatario paga total o parcialmente el préstamo.
     */
    @PostMapping("/{loanId}/repay")
    public ResponseEntity<LoanRepaymentResponse> repayLoan(
            @PathVariable UUID loanId,
            @Valid @RequestBody(required = false) RepayLoanRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        RepayLoanRequest safeRequest = request != null
                ? request
                : new RepayLoanRequest();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.repayLoan(
                        loanId,
                        safeRequest,
                        user.getUsername()
                ));
    }

    /**
     * PATCH /loans/{loanId}/cancel
     * Cancela préstamo no completado. Solo prestamista.
     */
    @PatchMapping("/{loanId}/cancel")
    public ResponseEntity<LoanResponse> cancelLoan(
            @PathVariable UUID loanId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                loanService.cancelLoan(
                        loanId,
                        user.getUsername()
                )
        );
    }
}
