package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.TopUpWalletRequest;
import com.yapeseguro.api.dto.response.PageResponse;
import com.yapeseguro.api.dto.response.TransactionResponse;
import com.yapeseguro.api.dto.response.WalletResponse;
import com.yapeseguro.application.services.TransactionService;
import com.yapeseguro.application.services.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    @GetMapping("/me")
    public ResponseEntity<List<WalletResponse>> getMyWallets(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                walletService.getMyWallets(user.getUsername())
        );
    }

    @PostMapping("/{walletId}/top-up")
    public ResponseEntity<WalletResponse> topUpWallet(
            @PathVariable UUID walletId,
            @Valid @RequestBody TopUpWalletRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                walletService.topUpWallet(
                        walletId,
                        request,
                        user.getUsername()
                )
        );
    }

    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<PageResponse<TransactionResponse>> getTransactions(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                transactionService.getWalletTransactions(
                        walletId,
                        user.getUsername(),
                        page,
                        size
                )
        );
    }
}