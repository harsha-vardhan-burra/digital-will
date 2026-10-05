package com.digitalwill.release.repository;

import com.digitalwill.release.model.ExecutionItemStatus;
import com.digitalwill.release.model.ReleaseExecutionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReleaseExecutionItemRepository extends JpaRepository<ReleaseExecutionItem, UUID> {
    List<ReleaseExecutionItem> findByExecutionId(UUID executionId);
    Optional<ReleaseExecutionItem> findByExecutionIdAndTargetIdAndItemType(UUID executionId, UUID targetId, String itemType);
    long countByExecutionIdAndStatus(UUID executionId, ExecutionItemStatus status);
}
