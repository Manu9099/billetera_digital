package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.GroupEntity;
import com.yapeseguro.infrastructure.persistence.entities.GroupMemberEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {

    List<GroupMemberEntity> findByGroupOrderByAddedAtAsc(GroupEntity group);

    Optional<GroupMemberEntity> findByIdAndGroup(UUID id, GroupEntity group);

    Optional<GroupMemberEntity> findByGroupAndUser(GroupEntity group, UserEntity user);

    boolean existsByGroupAndUser(GroupEntity group, UserEntity user);

    long countByGroup(GroupEntity group);
}