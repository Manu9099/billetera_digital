package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.DisputeTimelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputeTimelineRepository extends JpaRepository<DisputeTimelineEntity, UUID> {

    List<DisputeTimelineEntity> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);
}