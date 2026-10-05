package com.digitalwill.audit.repository;

import com.digitalwill.audit.model.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    Optional<AuditLogEntry> findTopByOrderBySequenceNumberDesc();

    List<AuditLogEntry> findByWillIdOrderBySequenceNumberAsc(UUID willId);

    List<AuditLogEntry> findAllByOrderBySequenceNumberAsc();

    @Query("SELECT COALESCE(MAX(a.sequenceNumber), 0) FROM AuditLogEntry a")
    long getMaxSequenceNumber();
}
