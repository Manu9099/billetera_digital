
package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfileEntity, UUID> {

    Optional<BusinessProfileEntity> findByUser(UserEntity user);

    Optional<BusinessProfileEntity> findByRuc(String ruc);

    Optional<BusinessProfileEntity> findByIdAndActiveTrue(UUID id);

    boolean existsByUser(UserEntity user);

    boolean existsByRuc(String ruc);
}
