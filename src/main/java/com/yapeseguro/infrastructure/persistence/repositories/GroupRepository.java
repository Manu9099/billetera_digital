package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.GroupEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

    @Query("""
            select distinct g
            from GroupEntity g
            left join g.members m
            where g.creatorUser = :user
               or m.user = :user
            order by g.createdAt desc
            """)
    List<GroupEntity> findVisibleToUser(@Param("user") UserEntity user);

    @Query("""
            select distinct g
            from GroupEntity g
            left join fetch g.members m
            left join fetch m.user
            where g.id = :groupId
              and (g.creatorUser = :user or m.user = :user)
            """)
    Optional<GroupEntity> findVisibleToUserDetailed(
            @Param("groupId") UUID groupId,
            @Param("user") UserEntity user
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select g
            from GroupEntity g
            join fetch g.creatorUser
            where g.id = :groupId
            """)
    Optional<GroupEntity> findByIdForUpdate(@Param("groupId") UUID groupId);
}