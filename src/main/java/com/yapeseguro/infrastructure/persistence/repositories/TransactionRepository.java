package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByReference(String reference);

    boolean existsByReference(String reference);

    @Query("""
            SELECT t
            FROM TransactionEntity t
            WHERE t.walletFrom = :wallet OR t.walletTo = :wallet
            ORDER BY t.createdAt DESC
            """)
    Page<TransactionEntity> findHistoryByWallet(
            @Param("wallet") WalletEntity wallet,
            Pageable pageable
    );
}