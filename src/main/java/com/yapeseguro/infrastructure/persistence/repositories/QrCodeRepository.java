package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.QrCodeEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QrCodeRepository extends JpaRepository<QrCodeEntity, UUID> {

    List<QrCodeEntity> findByCreatorUserAndActiveTrueOrderByCreatedAtDesc(
            UserEntity creatorUser
    );

    List<QrCodeEntity> findByCreatorWalletAndActiveTrueOrderByCreatedAtDesc(
            WalletEntity creatorWallet
    );

    Optional<QrCodeEntity> findByIdAndActiveTrue(UUID id);

    Optional<QrCodeEntity> findByIdAndCreatorWalletAndActiveTrue(
            UUID id,
            WalletEntity creatorWallet
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q
            from QrCodeEntity q
            join fetch q.creatorUser
            join fetch q.creatorWallet
            where q.id = :id
            """)
    Optional<QrCodeEntity> findByIdForUpdate(@Param("id") UUID id);
}