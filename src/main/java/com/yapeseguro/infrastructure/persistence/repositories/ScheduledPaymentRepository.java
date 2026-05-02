package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.ScheduledPaymentEntity;
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

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPaymentEntity, UUID> {

    List<ScheduledPaymentEntity> findByWalletFrom_UserOrderByCreatedAtDesc(UserEntity user);

    Optional<ScheduledPaymentEntity> findByIdAndWalletFrom_User(UUID id, UserEntity user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from ScheduledPaymentEntity s
            join fetch s.walletFrom wf
            join fetch wf.user
            left join fetch s.walletTo wt
            left join fetch wt.user
            left join fetch s.recipientUser
            where s.status = :status
              and s.autoPayEnabled = true
              and s.nextPaymentDate <= :now
            """)
    List<ScheduledPaymentEntity> findDueAutoPayForUpdate(
            @Param("status") ScheduledPaymentEntity.ScheduledStatus status,
            @Param("now") OffsetDateTime now
    );

    @Query("""
            select s
            from ScheduledPaymentEntity s
            join fetch s.walletFrom wf
            join fetch wf.user
            left join fetch s.recipientUser
            where s.status = :status
              and s.nextPaymentDate between :from and :to
            """)
    List<ScheduledPaymentEntity> findActiveUpcoming(
            @Param("status") ScheduledPaymentEntity.ScheduledStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}