package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.MarketplaceRequest;
import com.yapeseguro.api.dto.request.P2PRequest;
import com.yapeseguro.api.dto.response.TransactionReceiptResponse;
import com.yapeseguro.api.dto.response.TransactionResponse;
import com.yapeseguro.application.services.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * POST /transactions/p2p — transferencia P2P real.
     */
    @PostMapping("/p2p")
    public ResponseEntity<TransactionResponse> sendP2P(
            @Valid @RequestBody P2PRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.sendP2P(request, user.getUsername()));
    }

    /**
     * GET /transactions/{txId} — detalle de una transacción del usuario autenticado.
     */
    @GetMapping("/{txId}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                transactionService.getTransactionById(txId, user.getUsername())
        );
    }

    /**
     * GET /transactions/{txId}/receipt — comprobante JSON de una transacción del usuario autenticado.
     */
    @GetMapping("/{txId}/receipt")
    public ResponseEntity<TransactionReceiptResponse> getReceipt(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                transactionService.getReceipt(txId, user.getUsername())
        );
    }

    /**
     * POST /transactions/marketplace — reservado para Yape Seguro.
     */
    @PostMapping("/marketplace")
    public ResponseEntity<Void> marketplacePayment(
            @Valid @RequestBody MarketplaceRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * PATCH /transactions/{txId}/confirm-receipt — reservado para marketplace.
     */
    @PatchMapping("/{txId}/confirm-receipt")
    public ResponseEntity<Void> confirmReceipt(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}