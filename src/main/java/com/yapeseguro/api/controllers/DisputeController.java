package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateDisputeRequest;
import com.yapeseguro.api.dto.request.ResolveDisputeRequest;
import com.yapeseguro.api.dto.response.DisputeResponse;
import com.yapeseguro.application.services.DisputeService;
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
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * POST /disputes?transactionId={txId}
     * Abre una disputa marketplace usando endpoint formal de disputas.
     *
     * También existe:
     * POST /transactions/{txId}/dispute
     */
    @PostMapping
    public ResponseEntity<DisputeResponse> createDispute(
            @RequestParam UUID transactionId,
            @Valid @RequestBody CreateDisputeRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disputeService.openMarketplaceDispute(
                        transactionId,
                        request,
                        user.getUsername()
                ));
    }

    /**
     * GET /disputes/me
     * Lista mis disputas como comprador o vendedor.
     */
    @GetMapping("/me")
    public ResponseEntity<List<DisputeResponse>> getMyDisputes(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                disputeService.getMyDisputes(user.getUsername())
        );
    }

    /**
     * GET /disputes/{disputeId}
     * Obtiene una disputa por ID si el usuario participa en ella.
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeResponse> getDisputeById(
            @PathVariable UUID disputeId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                disputeService.getDisputeById(disputeId, user.getUsername())
        );
    }

    /**
     * PATCH /disputes/{disputeId}/resolve
     * Resuelve manualmente una disputa.
     *
     * resolution:
     * - REFUND
     * - PARTIAL_REFUND
     * - DISMISSED
     */
    @PatchMapping("/{disputeId}/resolve")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @PathVariable UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                disputeService.resolveMarketplaceDispute(
                        disputeId,
                        request,
                        user.getUsername()
                )
        );
    }
}