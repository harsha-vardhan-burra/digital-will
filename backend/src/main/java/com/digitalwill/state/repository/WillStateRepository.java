package com.digitalwill.state.repository;

import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WillStateEntity w WHERE w.id = :id")
    Optional<WillStateEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT w FROM WillStateEntity w WHERE w.state = com.digitalwill.state.model.WillState.ACTIVE AND w.lastVerifiedActivityAt <= :cutoff ORDER BY w.lastVerifiedActivityAt ASC")
    List<WillStateEntity> findEligibleForInactivityWarning(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT w FROM WillStateEntity w WHERE w.state = com.digitalwill.state.model.WillState.INACTIVITY_WARNING AND w.warningSentAt IS NOT NULL AND w.warningSentAt <= :cutoff ORDER BY w.warningSentAt ASC")
    List<WillStateEntity> findEligibleForFinalWarning(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT w FROM WillStateEntity w WHERE w.state = com.digitalwill.state.model.WillState.FINAL_WARNING AND w.finalWarningSentAt IS NOT NULL AND w.finalWarningSentAt <= :cutoff ORDER BY w.finalWarningSentAt ASC")
    List<WillStateEntity> findEligibleForVerification(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT w FROM WillStateEntity w WHERE w.state = com.digitalwill.state.model.WillState.RELEASE_PENDING AND w.releaseAfter <= :now AND w.cancelledAt IS NULL ORDER BY w.releaseAfter ASC")
    List<WillStateEntity> findEligibleForExecution(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT w FROM WillStateEntity w WHERE w.state = com.digitalwill.state.model.WillState.EXECUTING AND w.executingAt IS NOT NULL AND w.executingAt <= :cutoff ORDER BY w.executingAt ASC")
    List<WillStateEntity> findStalledExecutions(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable pageable);
}

