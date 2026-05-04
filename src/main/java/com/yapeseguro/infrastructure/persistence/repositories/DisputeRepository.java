package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.DisputeEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<DisputeEntity, UUID> {

    boolean existsByTransaction(TransactionEntity transaction);

    Optional<DisputeEntity> findByTransaction(TransactionEntity transaction);

    List<DisputeEntity> findByCreatedByUserOrRespondentUserOrderByOpenedAtDesc(
            UserEntity createdByUser,
            UserEntity respondentUser
    );

    @Query("""
            select d
            from DisputeEntity d
            join fetch d.transaction t
            join fetch t.walletFrom wf
            join fetch wf.user
            join fetch t.walletTo wt
            join fetch wt.user
            join fetch d.createdByUser
            join fetch d.respondentUser
            where d.id = :disputeId
            """)
    Optional<DisputeEntity> findDetailedById(
            @Param("disputeId") UUID disputeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d
            from DisputeEntity d
            join fetch d.transaction t
            join fetch t.walletFrom wf
            join fetch wf.user
            join fetch t.walletTo wt
            join fetch wt.user
            join fetch d.createdByUser
            join fetch d.respondentUser
            where d.id = :disputeId
            """)
    Optional<DisputeEntity> findDetailedByIdForUpdate(
            @Param("disputeId") UUID disputeId
    );

    @Query("""
            select d
            from DisputeEntity d
            join fetch d.transaction t
            join fetch t.walletFrom wf
            join fetch wf.user
            join fetch t.walletTo wt
            join fetch wt.user
            join fetch d.createdByUser
            join fetch d.respondentUser
            where t.id = :transactionId
            """)
    Optional<DisputeEntity> findDetailedByTransactionId(
            @Param("transactionId") UUID transactionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d
            from DisputeEntity d
            join fetch d.transaction t
            join fetch t.walletFrom wf
            join fetch wf.user
            join fetch t.walletTo wt
            join fetch wt.user
            join fetch d.createdByUser
            join fetch d.respondentUser
            where d.isMarketplaceDispute = true
              and d.status in :statuses
              and d.expiresAt <= :now
            """)
    List<DisputeEntity> findExpiredMarketplaceDisputesForUpdate(
            @Param("statuses") List<DisputeEntity.DisputeStatus> statuses,
            @Param("now") OffsetDateTime now
    );
}