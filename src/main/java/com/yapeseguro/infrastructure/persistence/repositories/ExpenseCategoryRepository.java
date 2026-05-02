package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.ExpenseCategoryEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, UUID> {

    List<ExpenseCategoryEntity> findByUserOrderByCategoryNameAsc(UserEntity user);

    Optional<ExpenseCategoryEntity> findByUserAndCategoryNameIgnoreCase(
            UserEntity user,
            String categoryName
    );

    boolean existsByUserAndCategoryNameIgnoreCase(
            UserEntity user,
            String categoryName
    );
}