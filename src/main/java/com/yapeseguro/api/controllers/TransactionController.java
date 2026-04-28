package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.MarketplaceRequest;
import com.yapeseguro.api.dto.request.P2PRequest;
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

    /**
     * POST /transactions/p2p — Feature: Pago P2P básico
     */
    @PostMapping("/p2p")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> sendP2P(
            @Valid @RequestBody P2PRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * POST /transactions/marketplace — Feature #1: Yape Seguro
     */
    @PostMapping("/marketplace")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> marketplacePayment(
            @Valid @RequestBody MarketplaceRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * PATCH /transactions/{txId}/confirm-receipt — comprador confirma
     */
    @PatchMapping("/{txId}/confirm-receipt")
    public ResponseEntity<Void> confirmReceipt(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /transactions/{txId}/receipt — comprobante
     */
    @GetMapping("/{txId}/receipt")
    public ResponseEntity<Void> getReceipt(@PathVariable UUID txId) {
        return ResponseEntity.ok().build();
    }
}