package com.yapeseguro.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    /**
     * GET /wallets/me — ver saldo de ambas billeteras
     */
    @GetMapping("/me")
    public ResponseEntity<Void> getMyWallets(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /wallets/{walletId}/transactions
     */
    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<Void> getTransactions(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }
}
