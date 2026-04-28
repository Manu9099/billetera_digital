package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.GoogleLoginRequest;
import com.yapeseguro.api.dto.request.LoginRequest;
import com.yapeseguro.api.dto.request.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    // TODO: inyectar AuthService

    /**
     * POST /auth/register
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        // authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        // authService.login(request)
        return ResponseEntity.ok().build();
    }

    /**
     * POST /auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        // authService.refresh(refreshToken)
        return ResponseEntity.ok().build();
    }

    /**
     * POST /auth/google
     */
    @PostMapping("/google")
    public ResponseEntity<Void> googleLogin(@RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok().build();
    }
}