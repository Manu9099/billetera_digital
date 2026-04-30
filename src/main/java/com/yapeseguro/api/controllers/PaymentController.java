package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.QrPaymentRequest;
import com.yapeseguro.api.dto.response.QrPaymentPreviewResponse;
import com.yapeseguro.api.dto.response.QrPaymentResponse;
import com.yapeseguro.application.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * GET /payments/business/qr/{qrId}/preview
     * Devuelve negocio, producto si existe, monto y disponibilidad.
     */
    @GetMapping("/business/qr/{qrId}/preview")
    public ResponseEntity<QrPaymentPreviewResponse> previewBusinessQrPayment(
            @PathVariable UUID qrId
    ) {
        return ResponseEntity.ok(
                paymentService.previewBusinessQrPayment(qrId)
        );
    }

    /**
     * POST /payments/business/qr
     * Paga un QR de negocio desde la billetera PERSONAL del usuario autenticado.
     */
    @PostMapping("/business/qr")
    public ResponseEntity<QrPaymentResponse> payBusinessQr(
            @Valid @RequestBody QrPaymentRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.payBusinessQr(request, user.getUsername()));
    }
}