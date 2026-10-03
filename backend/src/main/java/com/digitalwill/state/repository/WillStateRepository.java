package com.digitalwill.state.repository;

import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WillStateRepository extends JpaRepository<WillStateEntity, UUID> {

    List<WillStateEntity> findByState(WillState state);

    List<WillStateEntity> findByOwnerId(UUID ownerId);

    Optional<WillStateEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<WillStateEntity> findByStateAndReleaseAfterLessThanEqual(WillState state, Instant cutoff);
}
