package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.LoanEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<LoanEntity, UUID> {

    @Query("""
            select l
            from LoanEntity l
            join fetch l.borrowerUser
            join fetch l.lenderUser
            left join fetch l.transaction
            where l.borrowerUser = :user
               or l.lenderUser = :user
            order by l.createdAt desc
            """)
    List<LoanEntity> findVisibleToUser(@Param("user") UserEntity user);

    @Query("""
            select l
            from LoanEntity l
            join fetch l.borrowerUser
            join fetch l.lenderUser
            left join fetch l.transaction
            where l.id = :loanId
              and (l.borrowerUser = :user or l.lenderUser = :user)
            """)
    Optional<LoanEntity> findVisibleToUserById(
            @Param("loanId") UUID loanId,
            @Param("user") UserEntity user
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l
            from LoanEntity l
            join fetch l.borrowerUser
            join fetch l.lenderUser
            left join fetch l.transaction
            where l.id = :loanId
            """)
    Optional<LoanEntity> findByIdForUpdate(@Param("loanId") UUID loanId);

    List<LoanEntity> findByBorrowerUserAndLoanStatus(
            UserEntity borrowerUser,
            LoanEntity.LoanStatus loanStatus
    );

    List<LoanEntity> findByLenderUserAndLoanStatus(
            UserEntity lenderUser,
            LoanEntity.LoanStatus loanStatus
    );
}