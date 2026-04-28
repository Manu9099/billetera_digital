package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateDisputeRequest;
import com.yapeseguro.api.dto.request.ResolveDisputeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class DisputeController {

    /**
     * POST /disputes — abrir reclamo
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> createDispute(
            @Valid @RequestBody CreateDisputeRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /disputes/me — ver mis reclamos
     */
    @GetMapping("/me")
    public ResponseEntity<Void> getMyDisputes(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /disputes/{disputeId}
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<Void> getDisputeById(
            @PathVariable UUID disputeId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH /disputes/{disputeId}/resolve — resolver reclamo
     */
    @PatchMapping("/{disputeId}/resolve")
    public ResponseEntity<Void> resolveDispute(
            @PathVariable UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        return ResponseEntity.ok().build();
    }
}