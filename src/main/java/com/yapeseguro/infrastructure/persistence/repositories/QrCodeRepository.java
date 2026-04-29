package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.QrCodeEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QrCodeRepository extends JpaRepository<QrCodeEntity, UUID> {

    List<QrCodeEntity> findByCreatorUserAndActiveTrueOrderByCreatedAtDesc(UserEntity creatorUser);

    List<QrCodeEntity> findByCreatorWalletAndActiveTrueOrderByCreatedAtDesc(WalletEntity creatorWallet);
}