package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateScheduledPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/scheduled-payments")
@RequiredArgsConstructor
public class ScheduledPaymentController {

    /**
     * POST /scheduled-payments
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> create(
            @Valid @RequestBody CreateScheduledPaymentRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /scheduled-payments
     */
    @GetMapping
    public ResponseEntity<Void> list(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH /scheduled-payments/{id}/pause
     */
    @PatchMapping("/{id}/pause")
    public ResponseEntity<Void> pause(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /scheduled-payments/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID id
    ) {
        return ResponseEntity.noContent().build();
    }
}