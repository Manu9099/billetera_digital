package com.yapeseguro.api.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;




// ============================================================
// QrController — /qr  (Feature #9)
// ============================================================
@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
class QrController {

    /** POST /qr — crear QR con monto fijo */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> createFixedQR(
            @Valid @RequestBody CreateQrRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /qr/{id} — obtener detalles del QR para mostrar antes de pagar */
    @GetMapping("/{id}")
    public ResponseEntity<?> getQrDetails(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    /** POST /qr/{id}/pay — pagar QR escaneado */
    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payViaQR(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    @Data public static class CreateQrRequest {
        @NotBlank @Size(max = 255)   private String description;
        @DecimalMin("0.01")          private BigDecimal fixedAmount;
        @NotBlank                    private String qrType;   // PAYMENT, FIXED_AMOUNT, INVENTORY
    }
}

// ============================================================
// AnalyticsController — /analytics  (Feature #10)
// ============================================================
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
class AnalyticsController {

    /** GET /analytics/expenses — ranking de gastos del mes */
    @GetMapping("/expenses")
    public ResponseEntity<?> getExpenses(
            @RequestParam(defaultValue = "") String yearMonth,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /** GET /analytics/expenses/summary */
    @GetMapping("/expenses/summary")
    public ResponseEntity<?> getSummary(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }
}
