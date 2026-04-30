package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.AddInventoryItemRequest;
import com.yapeseguro.api.dto.request.CreateBusinessProfileRequest;
import com.yapeseguro.api.dto.request.UpdateBusinessProfileRequest;
import com.yapeseguro.api.dto.request.UpdateInventoryItemRequest;
import com.yapeseguro.api.dto.response.BusinessProfileResponse;
import com.yapeseguro.api.dto.response.InventoryItemResponse;
import com.yapeseguro.application.services.BusinessProfileService;
import com.yapeseguro.application.services.InventoryService;
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
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessProfileService businessProfileService;
    private final InventoryService inventoryService;

    /**
     * POST /business/profile — crea el perfil de negocio del usuario autenticado.
     */
    @PostMapping("/profile")
    public ResponseEntity<BusinessProfileResponse> createBusinessProfile(
            @Valid @RequestBody CreateBusinessProfileRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(businessProfileService.createProfile(request, user.getUsername()));
    }

    /**
     * GET /business/profile y GET /business/profile/me — devuelve mi perfil de negocio.
     */
    @GetMapping({"/profile", "/profile/me"})
    public ResponseEntity<BusinessProfileResponse> getMyBusinessProfile(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                businessProfileService.getMyProfile(user.getUsername())
        );
    }

    /**
     * PUT /business/profile y PUT /business/profile/me — actualiza mi perfil de negocio.
     */
    @PutMapping({"/profile", "/profile/me"})
    public ResponseEntity<BusinessProfileResponse> updateMyBusinessProfile(
            @Valid @RequestBody UpdateBusinessProfileRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                businessProfileService.updateMyProfile(request, user.getUsername())
        );
    }

    /**
     * GET /business/profile/{businessId} — perfil público activo.
     */
    @GetMapping("/profile/{businessId}")
    public ResponseEntity<BusinessProfileResponse> getBusinessProfileById(
            @PathVariable UUID businessId
    ) {
        return ResponseEntity.ok(
                businessProfileService.getProfileById(businessId)
        );
    }

    /**
     * GET /business/inventory — lista productos activos del negocio autenticado.
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryItemResponse>> getInventory(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                inventoryService.getMyInventory(user.getUsername())
        );
    }

    /**
     * POST /business/inventory — agrega producto al negocio autenticado.
     */
    @PostMapping("/inventory")
    public ResponseEntity<InventoryItemResponse> addInventoryItem(
            @Valid @RequestBody AddInventoryItemRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.addInventoryItem(request, user.getUsername()));
    }

    /**
     * GET /business/inventory/{itemId} — detalle de producto del negocio autenticado.
     */
    @GetMapping("/inventory/{itemId}")
    public ResponseEntity<InventoryItemResponse> getInventoryItem(
            @PathVariable UUID itemId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                inventoryService.getInventoryItem(itemId, user.getUsername())
        );
    }

    /**
     * PUT /business/inventory/{itemId} — actualiza producto del negocio autenticado.
     */
    @PutMapping("/inventory/{itemId}")
    public ResponseEntity<InventoryItemResponse> updateInventoryItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateInventoryItemRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                inventoryService.updateInventoryItem(itemId, request, user.getUsername())
        );
    }

    /**
     * DELETE /business/inventory/{itemId} — desactiva producto del negocio autenticado.
     */
    @DeleteMapping("/inventory/{itemId}")
    public ResponseEntity<Void> deleteInventoryItem(
            @PathVariable UUID itemId,
            @AuthenticationPrincipal UserDetails user
    ) {
        inventoryService.deactivateInventoryItem(itemId, user.getUsername());

        return ResponseEntity.noContent().build();
    }

    /**
     * GET /business/analytics — reservado para Analytics negocio.
     */
    @GetMapping("/analytics")
    public ResponseEntity<Void> getBusinessAnalytics(
            @RequestParam(defaultValue = "2024-01") String yearMonth,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}