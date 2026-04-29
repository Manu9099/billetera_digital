package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.GoogleLoginRequest;
import com.yapeseguro.api.dto.request.LoginRequest;
import com.yapeseguro.api.dto.request.RegisterRequest;
import com.yapeseguro.api.dto.response.AuthResponse;
import com.yapeseguro.application.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/register
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
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