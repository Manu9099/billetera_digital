package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.LoginRequest;
import com.yapeseguro.api.dto.request.RegisterRequest;
import com.yapeseguro.api.dto.response.AuthResponse;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import com.yapeseguro.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateUniqueUser(request);

        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .reniecId(request.getReniecId())
                .kycStatus(UserEntity.KycStatus.PENDING)
                .phoneVerified(false)
                .biometricEnabled(false)
                .interfaceMode(UserEntity.InterfaceMode.STANDARD)
                .deleted(false)
                .build();

        UserEntity savedUser = userRepository.save(user);

        createDefaultWallets(savedUser);

        String accessToken = generateToken(savedUser);

        return toAuthResponse(savedUser, accessToken);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmailOrPhone())
                .or(() -> userRepository.findByPhoneNumber(request.getEmailOrPhone()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (user.isDeleted()) {
            throw new BadCredentialsException("Usuario deshabilitado");
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        String accessToken = generateToken(user);

        return toAuthResponse(user, accessToken);
    }

    private void validateUniqueUser(RegisterRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            throw new IllegalArgumentException("El email ya está registrado");
        });

        userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(user -> {
            throw new IllegalArgumentException("El número de celular ya está registrado");
        });

        if (request.getReniecId() != null && !request.getReniecId().isBlank()) {
            userRepository.findByReniecId(request.getReniecId()).ifPresent(user -> {
                throw new IllegalArgumentException("El DNI ya está registrado");
            });
        }
    }

    private void createDefaultWallets(UserEntity user) {
        WalletEntity personalWallet = WalletEntity.builder()
                .user(user)
                .walletType(WalletEntity.WalletType.PERSONAL)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .holdAmount(BigDecimal.ZERO)
                .currency("PEN")
                .monthlyRevenue(BigDecimal.ZERO)
                .monthlyExpenses(BigDecimal.ZERO)
                .dailyTxCount(0)
                .active(true)
                .build();

        WalletEntity businessWallet = WalletEntity.builder()
                .user(user)
                .walletType(WalletEntity.WalletType.BUSINESS)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .holdAmount(BigDecimal.ZERO)
                .currency("PEN")
                .monthlyRevenue(BigDecimal.ZERO)
                .monthlyExpenses(BigDecimal.ZERO)
                .dailyTxCount(0)
                .active(true)
                .build();

        walletRepository.save(personalWallet);
        walletRepository.save(businessWallet);
    }

    private String generateToken(UserEntity user) {
        return jwtService.generateToken(user.getEmail());
    }

    private AuthResponse toAuthResponse(UserEntity user, String accessToken) {
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .accessToken(accessToken)
                .tokenType("Bearer")
                .build();
    }
}