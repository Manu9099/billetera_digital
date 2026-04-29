package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.TopUpWalletRequest;
import com.yapeseguro.api.dto.response.WalletResponse;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public List<WalletResponse> getMyWallets(String username) {
        UserEntity user = getUserByUsername(username);

        return walletRepository.findByUser(user)
                .stream()
                .sorted(Comparator.comparingInt(this::walletOrder))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WalletResponse topUpWallet(UUID walletId, TopUpWalletRequest request, String username) {
        UserEntity user = getUserByUsername(username);

        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada"));

        if (!wallet.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("No tienes permiso para recargar esta billetera");
        }

        if (!wallet.isActive()) {
            throw new IllegalArgumentException("La billetera no está activa");
        }

        BigDecimal amount = request.getAmount();

        BigDecimal currentBalance = safe(wallet.getBalance());
        BigDecimal currentAvailableBalance = safe(wallet.getAvailableBalance());

        wallet.setBalance(currentBalance.add(amount));
        wallet.setAvailableBalance(currentAvailableBalance.add(amount));

        WalletEntity savedWallet = walletRepository.save(wallet);

        return toResponse(savedWallet);
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private int walletOrder(WalletEntity wallet) {
        if (wallet.getWalletType() == WalletEntity.WalletType.PERSONAL) {
            return 1;
        }

        if (wallet.getWalletType() == WalletEntity.WalletType.BUSINESS) {
            return 2;
        }

        return 99;
    }

    private WalletResponse toResponse(WalletEntity wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .walletType(wallet.getWalletType().name())
                .balance(wallet.getBalance())
                .availableBalance(wallet.getAvailableBalance())
                .holdAmount(wallet.getHoldAmount())
                .currency(wallet.getCurrency())
                .monthlyRevenue(wallet.getMonthlyRevenue())
                .monthlyExpenses(wallet.getMonthlyExpenses())
                .dailyTxCount(wallet.getDailyTxCount())
                .active(wallet.isActive())
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}