package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Page<TransactionEntity> findByWalletFromOrWalletTo(
            WalletEntity walletFrom,
            WalletEntity walletTo,
            Pageable pageable
    );

    boolean existsByReference(String reference);
}