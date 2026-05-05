package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.BusinessReviewEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BusinessReviewRepository extends JpaRepository<BusinessReviewEntity, UUID> {

    boolean existsByTransaction(TransactionEntity transaction);

    Optional<BusinessReviewEntity> findByTransaction(TransactionEntity transaction);

    Optional<BusinessReviewEntity> findByTransactionAndCustomer(
            TransactionEntity transaction,
            UserEntity customer
    );

    Optional<BusinessReviewEntity> findByIdAndCustomer(
            UUID id,
            UserEntity customer
    );

    Page<BusinessReviewEntity> findByBusinessProfileAndStatusOrderByCreatedAtDesc(
            BusinessProfileEntity businessProfile,
            BusinessReviewEntity.ReviewStatus status,
            Pageable pageable
    );

    @Query("""
            select avg(r.rating), count(r)
            from BusinessReviewEntity r
            where r.businessProfile = :businessProfile
              and r.status = :status
            """)
    Object[] calculateRatingStats(
            @Param("businessProfile") BusinessProfileEntity businessProfile,
            @Param("status") BusinessReviewEntity.ReviewStatus status
    );
}