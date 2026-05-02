package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.LoanEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<LoanEntity, UUID> {

    List<LoanEntity> findByBorrowerUserOrLenderUserOrderByCreatedAtDesc(
            UserEntity borrowerUser,
            UserEntity lenderUser
    );

    List<LoanEntity> findByBorrowerUserOrderByCreatedAtDesc(
            UserEntity borrowerUser
    );

    List<LoanEntity> findByLenderUserOrderByCreatedAtDesc(
            UserEntity lenderUser
    );
}