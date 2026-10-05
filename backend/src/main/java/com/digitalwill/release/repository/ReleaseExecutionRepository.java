package com.digitalwill.release.repository;

import com.digitalwill.release.model.ExecutionStatus;
import com.digitalwill.release.model.ReleaseExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReleaseExecutionRepository extends JpaRepository<ReleaseExecution, UUID> {
    Optional<ReleaseExecution> findByWillId(UUID willId);
    List<ReleaseExecution> findByStatus(ExecutionStatus status);
}
