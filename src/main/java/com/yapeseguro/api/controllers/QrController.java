package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateQrRequest;
import com.yapeseguro.api.dto.request.QrPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
public class QrController {

    /**
     * POST /qr — crear QR
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> createQr(
            @Valid @RequestBody CreateQrRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /qr/me — mis QR
     */
    @GetMapping("/me")
    public ResponseEntity<Void> getMyQrCodes(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /qr/{qrId}
     */
    @GetMapping("/{qrId}")
    public ResponseEntity<Void> getQrById(
            @PathVariable UUID qrId
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * POST /qr/pay — pagar QR
     */
    @PostMapping("/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> payQr(
            @Valid @RequestBody QrPaymentRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * PATCH /qr/{qrId}/disable
     */
    @PatchMapping("/{qrId}/disable")
    public ResponseEntity<Void> disableQr(
            @PathVariable UUID qrId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }
}