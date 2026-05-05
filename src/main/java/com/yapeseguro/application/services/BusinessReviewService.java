package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateBusinessReviewRequest;
import com.yapeseguro.api.dto.request.UpdateBusinessReviewRequest;
import com.yapeseguro.api.dto.response.BusinessReviewResponse;
import com.yapeseguro.api.dto.response.BusinessReviewSummaryResponse;
import com.yapeseguro.api.dto.response.PageResponse;
import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.BusinessReviewEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessProfileRepository;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessReviewRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessReviewService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final BusinessReviewRepository businessReviewRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public BusinessReviewResponse createReview(
            UUID businessProfileId,
            CreateBusinessReviewRequest request,
            String username
    ) {
        UserEntity customer = getUserByUsername(username);

        BusinessProfileEntity businessProfile = businessProfileRepository.findByIdAndActiveTrue(businessProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));

        TransactionEntity transaction = transactionRepository.findDetailedById(request.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionCanBeReviewed(transaction, businessProfile, customer);

        if (businessReviewRepository.existsByTransaction(transaction)) {
            throw new IllegalArgumentException("Esta transacción ya tiene una reseña registrada");
        }

        BusinessReviewEntity review = BusinessReviewEntity.builder()
                .businessProfile(businessProfile)
                .transaction(transaction)
                .customer(customer)
                .rating(request.getRating())
                .reviewComment(normalize(request.getComment()))
                .status(BusinessReviewEntity.ReviewStatus.VISIBLE)
                .build();

        BusinessReviewEntity savedReview = businessReviewRepository.save(review);

        recalculateBusinessRating(businessProfile);

        return toResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public PageResponse<BusinessReviewResponse> getBusinessReviews(
            UUID businessProfileId,
            Integer page,
            Integer size
    ) {
        BusinessProfileEntity businessProfile = businessProfileRepository.findByIdAndActiveTrue(businessProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));

        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);

        Page<BusinessReviewEntity> reviews = businessReviewRepository
                .findByBusinessProfileAndStatusOrderByCreatedAtDesc(
                        businessProfile,
                        BusinessReviewEntity.ReviewStatus.VISIBLE,
                        PageRequest.of(safePage, safeSize)
                );

        return PageResponse.<BusinessReviewResponse>builder()
                .content(
                        reviews.getContent()
                                .stream()
                                .map(this::toResponse)
                                .toList()
                )
                .page(reviews.getNumber())
                .size(reviews.getSize())
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .first(reviews.isFirst())
                .last(reviews.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public BusinessReviewSummaryResponse getBusinessReviewSummary(UUID businessProfileId) {
        BusinessProfileEntity businessProfile = businessProfileRepository.findByIdAndActiveTrue(businessProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));

        return BusinessReviewSummaryResponse.builder()
                .businessProfileId(businessProfile.getId())
                .businessName(businessProfile.getBusinessName())
                .averageRating(safe(businessProfile.getAverageRating()))
                .totalReviews(businessProfile.getTotalReviews() != null ? businessProfile.getTotalReviews() : 0)
                .build();
    }

    @Transactional(readOnly = true)
    public BusinessReviewResponse getMyReviewByTransaction(
            UUID transactionId,
            String username
    ) {
        UserEntity customer = getUserByUsername(username);

        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        BusinessReviewEntity review = businessReviewRepository
                .findByTransactionAndCustomer(transaction, customer)
                .orElseThrow(() -> new IllegalArgumentException("Reseña no encontrada"));

        if (review.getStatus() == BusinessReviewEntity.ReviewStatus.DELETED) {
            throw new IllegalArgumentException("Reseña no encontrada");
        }

        return toResponse(review);
    }

    @Transactional
    public BusinessReviewResponse updateReview(
            UUID reviewId,
            UpdateBusinessReviewRequest request,
            String username
    ) {
        UserEntity customer = getUserByUsername(username);

        BusinessReviewEntity review = businessReviewRepository
                .findByIdAndCustomer(reviewId, customer)
                .orElseThrow(() -> new IllegalArgumentException("Reseña no encontrada"));

        if (review.getStatus() == BusinessReviewEntity.ReviewStatus.DELETED) {
            throw new IllegalArgumentException("Reseña no encontrada");
        }

        review.setRating(request.getRating());
        review.setReviewComment(normalize(request.getComment()));
        review.setStatus(BusinessReviewEntity.ReviewStatus.VISIBLE);

        BusinessReviewEntity savedReview = businessReviewRepository.save(review);

        recalculateBusinessRating(savedReview.getBusinessProfile());

        return toResponse(savedReview);
    }

    @Transactional
    public void deleteReview(
            UUID reviewId,
            String username
    ) {
        UserEntity customer = getUserByUsername(username);

        BusinessReviewEntity review = businessReviewRepository
                .findByIdAndCustomer(reviewId, customer)
                .orElseThrow(() -> new IllegalArgumentException("Reseña no encontrada"));

        if (review.getStatus() == BusinessReviewEntity.ReviewStatus.DELETED) {
            return;
        }

        review.setStatus(BusinessReviewEntity.ReviewStatus.DELETED);
        businessReviewRepository.save(review);

        recalculateBusinessRating(review.getBusinessProfile());
    }

    private void validateTransactionCanBeReviewed(
            TransactionEntity transaction,
            BusinessProfileEntity businessProfile,
            UserEntity customer
    ) {
        boolean customerIsSender = transaction.getWalletFrom()
                .getUser()
                .getId()
                .equals(customer.getId());

        if (!customerIsSender) {
            throw new IllegalArgumentException("Solo el comprador puede reseñar esta operación");
        }

        boolean sellerIsBusiness = transaction.getWalletTo()
                .getId()
                .equals(businessProfile.getBusinessWallet().getId());

        if (!sellerIsBusiness) {
            throw new IllegalArgumentException("La transacción no pertenece a este negocio");
        }

        boolean ownerTryingToReview = businessProfile.getUser()
                .getId()
                .equals(customer.getId());

        if (ownerTryingToReview) {
            throw new IllegalArgumentException("El dueño del negocio no puede reseñar su propio negocio");
        }

        boolean finalStatus =
                transaction.getStatus() == TransactionEntity.TxStatus.COMPLETED
                        || transaction.getStatus() == TransactionEntity.TxStatus.RELEASED;

        if (!finalStatus) {
            throw new IllegalArgumentException("Solo se pueden reseñar transacciones finalizadas");
        }

        boolean reviewableType =
                transaction.getType() == TransactionEntity.TxType.QR_PAYMENT
                        || transaction.getType() == TransactionEntity.TxType.MARKETPLACE;

        if (!reviewableType) {
            throw new IllegalArgumentException("Solo se pueden reseñar pagos QR o marketplace");
        }
    }

    private void recalculateBusinessRating(BusinessProfileEntity businessProfile) {
        Object[] stats = businessReviewRepository.calculateRatingStats(
                businessProfile,
                BusinessReviewEntity.ReviewStatus.VISIBLE
        );

        Double average = stats != null && stats.length > 0 && stats[0] != null
                ? ((Number) stats[0]).doubleValue()
                : 0.0;

        Integer totalReviews = stats != null && stats.length > 1 && stats[1] != null
                ? ((Number) stats[1]).intValue()
                : 0;

        businessProfile.setAverageRating(
                BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP)
        );

        businessProfile.setTotalReviews(totalReviews);

        businessProfileRepository.save(businessProfile);
    }

    private BusinessReviewResponse toResponse(BusinessReviewEntity review) {
        BusinessProfileEntity businessProfile = review.getBusinessProfile();
        TransactionEntity transaction = review.getTransaction();
        UserEntity customer = review.getCustomer();

        return BusinessReviewResponse.builder()
                .id(review.getId())
                .businessProfileId(businessProfile.getId())
                .businessName(businessProfile.getBusinessName())
                .transactionId(transaction.getId())
                .transactionReference(transaction.getReference())
                .customerUserId(customer.getId())
                .customerName(fullName(customer))
                .rating(review.getRating())
                .comment(review.getReviewComment())
                .status(review.getStatus().name())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }

        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String fullName(UserEntity user) {
        return (nullToDash(user.getFirstName()) + " " + nullToDash(user.getLastName())).trim();
    }

    private String nullToDash(String value) {
        String normalized = normalize(value);

        return normalized != null ? normalized : "-";
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}