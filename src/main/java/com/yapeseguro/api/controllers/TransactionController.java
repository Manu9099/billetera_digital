package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateDisputeRequest;
import com.yapeseguro.api.dto.request.MarketplaceRequest;
import com.yapeseguro.api.dto.request.P2PRequest;
import com.yapeseguro.api.dto.response.DisputeResponse;
import com.yapeseguro.api.dto.response.TransactionReceiptResponse;
import com.yapeseguro.api.dto.response.TransactionResponse;
import com.yapeseguro.application.services.DisputeService;
import com.yapeseguro.application.services.TransactionService;
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
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final DisputeService disputeService;

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
     * POST /transactions/marketplace — pago protegido con dinero retenido.
     */
    @PostMapping("/marketplace")
    public ResponseEntity<TransactionResponse> marketplacePayment(
            @Valid @RequestBody MarketplaceRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.createMarketplacePayment(
                        request,
                        user.getUsername()
                ));
    }

    /**
     * PATCH /transactions/{txId}/confirm-receipt — comprador confirma recepción y libera el dinero.
     */
    @PatchMapping("/{txId}/confirm-receipt")
    public ResponseEntity<TransactionResponse> confirmReceipt(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                transactionService.confirmMarketplaceReceipt(
                        txId,
                        user.getUsername()
                )
        );
    }

    /**
     * POST /transactions/{txId}/dispute — comprador abre disputa marketplace.
     */
    @PostMapping("/{txId}/dispute")
    public ResponseEntity<DisputeResponse> openDispute(
            @PathVariable UUID txId,
            @Valid @RequestBody CreateDisputeRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disputeService.openMarketplaceDispute(
                        txId,
                        request,
                        user.getUsername()
                ));
    }

    /**
     * GET /transactions/disputes/me — disputas donde soy comprador o vendedor.
     */
    @GetMapping("/disputes/me")
    public ResponseEntity<List<DisputeResponse>> getMyDisputes(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                disputeService.getMyDisputes(user.getUsername())
        );
    }

    /**
     * GET /transactions/{txId}/dispute — obtiene disputa de una transacción.
     */
    @GetMapping("/{txId}/dispute")
    public ResponseEntity<DisputeResponse> getTransactionDispute(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                disputeService.getTransactionDispute(
                        txId,
                        user.getUsername()
                )
        );
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
}