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

    @Query(
            value = """
                    select t
                    from TransactionEntity t
                    join fetch t.walletFrom wf
                    join fetch wf.user
                    join fetch t.walletTo wt
                    join fetch wt.user
                    where t.walletFrom = :wallet
                       or t.walletTo = :wallet
                    """,
            countQuery = """
                    select count(t)
                    from TransactionEntity t
                    where t.walletFrom = :wallet
                       or t.walletTo = :wallet
                    """
    )
    Page<TransactionEntity> findHistoryByWallet(
            @Param("wallet") WalletEntity wallet,
            Pageable pageable
    );

    @Query("""
            select t
            from TransactionEntity t
            join fetch t.walletFrom wf
            join fetch wf.user
            join fetch t.walletTo wt
            join fetch wt.user
            where t.id = :id
            """)
    Optional<TransactionEntity> findDetailedById(@Param("id") UUID id);
}