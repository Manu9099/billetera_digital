package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {

    List<WalletEntity> findByUser(UserEntity user);

    Optional<WalletEntity> findByUserAndWalletType(
            UserEntity user,
            WalletEntity.WalletType walletType
    );
}