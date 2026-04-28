package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.AddInventoryItemRequest;
import com.yapeseguro.api.dto.request.CreateBusinessProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessController {

    /**
     * POST /business/profile — crear perfil de negocio
     */
    @PostMapping("/profile")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> createBusinessProfile(
            @Valid @RequestBody CreateBusinessProfileRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /business/profile — mi perfil de negocio
     */
    @GetMapping("/profile")
    public ResponseEntity<Void> getMyBusinessProfile(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /business/inventory — inventario
     */
    @GetMapping("/inventory")
    public ResponseEntity<Void> getInventory(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * POST /business/inventory — agregar producto
     */
    @PostMapping("/inventory")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> addInventoryItem(
            @Valid @RequestBody AddInventoryItemRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /business/analytics — analytics del negocio
     */
    @GetMapping("/analytics")
    public ResponseEntity<Void> getBusinessAnalytics(
            @RequestParam(defaultValue = "2024-01") String yearMonth,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }
}