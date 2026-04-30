package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.MarketplaceRequest;
import com.yapeseguro.api.dto.request.P2PRequest;
import com.yapeseguro.api.dto.response.TransactionResponse;
import com.yapeseguro.application.services.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * POST /transactions/p2p
     * Transferencia P2P real entre billeteras.
     */
    @PostMapping("/p2p")
    public ResponseEntity<TransactionResponse> sendP2P(
            @Valid @RequestBody P2PRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        TransactionResponse response = transactionService.sendP2P(
                request,
                user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /transactions/marketplace
     * Pendiente: flujo escrow / marketplace.
     *
     * Se deja explícitamente como 501 para no devolver un 201 falso
     * mientras la lógica real aún no esté implementada.
     */
    @PostMapping("/marketplace")
    public ResponseEntity<Map<String, String>> marketplacePayment(
            @Valid @RequestBody MarketplaceRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "code", "MARKETPLACE_NOT_IMPLEMENTED",
                        "message", "El flujo marketplace todavía no está implementado."
                ));
    }

    /**
     * PATCH /transactions/{txId}/confirm-receipt
     * Pendiente: confirmación de recepción del comprador.
     */
    @PatchMapping("/{txId}/confirm-receipt")
    public ResponseEntity<Map<String, String>> confirmReceipt(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "code", "CONFIRM_RECEIPT_NOT_IMPLEMENTED",
                        "message", "La confirmación de recepción todavía no está implementada."
                ));
    }

    /**
     * GET /transactions/{txId}/receipt
     * Pendiente: generación de comprobante.
     */
    @GetMapping("/{txId}/receipt")
    public ResponseEntity<Map<String, String>> getReceipt(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "code", "RECEIPT_NOT_IMPLEMENTED",
                        "message", "El comprobante de transacción todavía no está implementado."
                ));
    }
}