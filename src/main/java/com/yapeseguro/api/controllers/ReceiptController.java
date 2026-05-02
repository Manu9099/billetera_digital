package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.response.ReceiptResponse;
import com.yapeseguro.api.dto.response.ReceiptValidationResponse;
import com.yapeseguro.application.services.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    /**
     * GET /receipts/transactions/{txId}
     * Devuelve comprobante persistido en JSON.
     */
    @GetMapping("/transactions/{txId}")
    public ResponseEntity<ReceiptResponse> getReceiptByTransaction(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                receiptService.getReceiptByTransaction(
                        txId,
                        user.getUsername()
                )
        );
    }

    /**
     * GET /receipts/transactions/{txId}/html
     * Devuelve comprobante HTML renderizable.
     */
    @GetMapping(
            value = "/transactions/{txId}/html",
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> getReceiptHtml(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                receiptService.getReceiptHtmlByTransaction(
                        txId,
                        user.getUsername()
                )
        );
    }

    /**
     * GET /receipts/transactions/{txId}/pdf
     * Devuelve PDF básico generado sin dependencias externas.
     */
    @GetMapping(
            value = "/transactions/{txId}/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> getReceiptPdf(
            @PathVariable UUID txId,
            @AuthenticationPrincipal UserDetails user
    ) {
        byte[] pdf = receiptService.getReceiptPdfByTransaction(
                txId,
                user.getUsername()
        );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"receipt-" + txId + ".pdf\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * GET /receipts/validate/{receiptNumber}
     * Valida un comprobante por número.
     */
    @GetMapping("/validate/{receiptNumber}")
    public ResponseEntity<ReceiptValidationResponse> validateReceipt(
            @PathVariable String receiptNumber
    ) {
        return ResponseEntity.ok(
                receiptService.validateReceipt(receiptNumber)
        );
    }
}