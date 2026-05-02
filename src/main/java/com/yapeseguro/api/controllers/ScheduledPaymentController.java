package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateScheduledPaymentRequest;
import com.yapeseguro.api.dto.response.ScheduledPaymentResponse;
import com.yapeseguro.application.services.ScheduledPaymentService;
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
@RequestMapping("/scheduled-payments")
@RequiredArgsConstructor
public class ScheduledPaymentController {

    private final ScheduledPaymentService scheduledPaymentService;

    @PostMapping
    public ResponseEntity<ScheduledPaymentResponse> create(
            @Valid @RequestBody CreateScheduledPaymentRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduledPaymentService.createScheduledPayment(
                        request,
                        user.getUsername()
                ));
    }

    @GetMapping
    public ResponseEntity<List<ScheduledPaymentResponse>> list(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                scheduledPaymentService.getMyScheduledPayments(user.getUsername())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduledPaymentResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                scheduledPaymentService.getMyScheduledPayment(
                        id,
                        user.getUsername()
                )
        );
    }

    @PostMapping("/{id}/pay-now")
    public ResponseEntity<ScheduledPaymentResponse> payNow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                scheduledPaymentService.payNow(
                        id,
                        user.getUsername()
                )
        );
    }

    @PatchMapping("/{id}/pause")
    public ResponseEntity<ScheduledPaymentResponse> pause(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                scheduledPaymentService.pauseScheduledPayment(
                        id,
                        user.getUsername()
                )
        );
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<ScheduledPaymentResponse> resume(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                scheduledPaymentService.resumeScheduledPayment(
                        id,
                        user.getUsername()
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        scheduledPaymentService.cancelScheduledPayment(
                id,
                user.getUsername()
        );

        return ResponseEntity.noContent().build();
    }
}