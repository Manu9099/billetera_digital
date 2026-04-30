package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateBusinessProfileRequest;
import com.yapeseguro.api.dto.request.UpdateBusinessProfileRequest;
import com.yapeseguro.api.dto.response.BusinessProfileResponse;
import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessProfileRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessProfileService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final BusinessProfileRepository businessProfileRepository;

    @Transactional
    public BusinessProfileResponse createProfile(
            CreateBusinessProfileRequest request,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        if (businessProfileRepository.existsByUser(user)) {
            throw new IllegalArgumentException("El usuario ya tiene un perfil de negocio");
        }

        if (businessProfileRepository.existsByRuc(request.getRuc())) {
            throw new IllegalArgumentException("El RUC ya está registrado");
        }

        WalletEntity businessWallet = getOrCreateBusinessWallet(user);

        BusinessProfileEntity profile = BusinessProfileEntity.builder()
                .user(user)
                .businessWallet(businessWallet)
                .businessName(normalizeRequired(request.getBusinessName(), "El nombre comercial es obligatorio"))
                .ruc(request.getRuc())
                .businessCategory(normalizeRequired(request.getBusinessCategory(), "La categoría del negocio es obligatoria"))
                .description(normalize(request.getDescription()))
                .address(normalize(request.getAddress()))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .city(normalize(request.getCity()))
                .district(normalize(request.getDistrict()))
                .businessPhoneNumber(normalize(request.getBusinessPhoneNumber()))
                .businessEmail(normalize(request.getBusinessEmail()))
                .website(normalize(request.getWebsite()))
                .verificationStatus(BusinessProfileEntity.VerificationStatus.PENDING)
                .averageRating(BigDecimal.ZERO)
                .totalReviews(0)
                .totalTransactions(0)
                .totalRevenue(BigDecimal.ZERO)
                .autoConfirmReceipts(false)
                .showFrequentCustomers(false)
                .active(true)
                .build();

        return toResponse(businessProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse getMyProfile(String username) {
        UserEntity user = getUserByUsername(username);

        BusinessProfileEntity profile = businessProfileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("No tienes un perfil de negocio creado"));

        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse getProfileById(UUID businessId) {
        BusinessProfileEntity profile = businessProfileRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Perfil de negocio no encontrado"));

        return toResponse(profile);
    }

    @Transactional
    public BusinessProfileResponse updateMyProfile(
            UpdateBusinessProfileRequest request,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        BusinessProfileEntity profile = businessProfileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("No tienes un perfil de negocio creado"));

        updateRucIfPresent(profile, request.getRuc());
        updateStringIfPresent(
                request.getBusinessName(),
                value -> profile.setBusinessName(normalizeRequired(value, "El nombre comercial no puede estar vacío"))
        );
        updateStringIfPresent(
                request.getBusinessCategory(),
                value -> profile.setBusinessCategory(normalizeRequired(value, "La categoría del negocio no puede estar vacía"))
        );
        updateStringIfPresent(request.getDescription(), value -> profile.setDescription(normalize(value)));
        updateStringIfPresent(request.getAddress(), value -> profile.setAddress(normalize(value)));
        updateStringIfPresent(request.getCity(), value -> profile.setCity(normalize(value)));
        updateStringIfPresent(request.getDistrict(), value -> profile.setDistrict(normalize(value)));
        updateStringIfPresent(request.getBusinessPhoneNumber(), value -> profile.setBusinessPhoneNumber(normalize(value)));
        updateStringIfPresent(request.getBusinessEmail(), value -> profile.setBusinessEmail(normalize(value)));
        updateStringIfPresent(request.getWebsite(), value -> profile.setWebsite(normalize(value)));

        if (request.getLatitude() != null) {
            profile.setLatitude(request.getLatitude());
        }

        if (request.getLongitude() != null) {
            profile.setLongitude(request.getLongitude());
        }

        if (request.getAutoConfirmReceipts() != null) {
            profile.setAutoConfirmReceipts(request.getAutoConfirmReceipts());
        }

        if (request.getShowFrequentCustomers() != null) {
            profile.setShowFrequentCustomers(request.getShowFrequentCustomers());
        }

        if (request.getActive() != null) {
            profile.setActive(request.getActive());
        }

        return toResponse(businessProfileRepository.save(profile));
    }

    private void updateRucIfPresent(BusinessProfileEntity profile, String ruc) {
        if (ruc == null) {
            return;
        }

        String normalizedRuc = normalizeRequired(ruc, "El RUC no puede estar vacío");

        businessProfileRepository.findByRuc(normalizedRuc)
                .filter(existingProfile -> !existingProfile.getId().equals(profile.getId()))
                .ifPresent(existingProfile -> {
                    throw new IllegalArgumentException("El RUC ya está registrado");
                });

        profile.setRuc(normalizedRuc);
    }

    private WalletEntity getOrCreateBusinessWallet(UserEntity user) {
        return walletRepository
                .findByUserAndWalletType(user, WalletEntity.WalletType.BUSINESS)
                .map(wallet -> {
                    if (!wallet.isActive()) {
                        wallet.setActive(true);
                        return walletRepository.save(wallet);
                    }

                    return wallet;
                })
                .orElseGet(() -> walletRepository.save(buildBusinessWallet(user)));
    }

    private WalletEntity buildBusinessWallet(UserEntity user) {
        return WalletEntity.builder()
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
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private BusinessProfileResponse toResponse(BusinessProfileEntity profile) {
        return BusinessProfileResponse.builder()
                .id(profile.getId())
                .ownerUserId(profile.getUser().getId())
                .businessWalletId(profile.getBusinessWallet().getId())
                .businessName(profile.getBusinessName())
                .ruc(profile.getRuc())
                .businessCategory(profile.getBusinessCategory())
                .description(profile.getDescription())
                .address(profile.getAddress())
                .latitude(profile.getLatitude())
                .longitude(profile.getLongitude())
                .city(profile.getCity())
                .district(profile.getDistrict())
                .businessPhoneNumber(profile.getBusinessPhoneNumber())
                .businessEmail(profile.getBusinessEmail())
                .website(profile.getWebsite())
                .verificationStatus(profile.getVerificationStatus().name())
                .verificationDate(profile.getVerificationDate())
                .averageRating(profile.getAverageRating())
                .totalReviews(profile.getTotalReviews())
                .totalTransactions(profile.getTotalTransactions())
                .totalRevenue(profile.getTotalRevenue())
                .autoConfirmReceipts(profile.isAutoConfirmReceipts())
                .showFrequentCustomers(profile.isShowFrequentCustomers())
                .active(profile.isActive())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);

        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }

        return normalized;
    }

    private void updateStringIfPresent(String value, StringUpdater updater) {
        if (value != null) {
            updater.update(value);
        }
    }

    @FunctionalInterface
    private interface StringUpdater {
        void update(String value);
    }
}
