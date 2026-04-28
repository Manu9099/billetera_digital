package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateGroupRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    /**
     * POST /groups
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /groups — mis grupos
     */
    @GetMapping
    public ResponseEntity<Void> getMyGroups(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * POST /groups/{id}/pay — pagar al grupo
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<Void> payGroup(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }
}