package com.digitalwill.release.repository;

import com.digitalwill.release.model.DisclosureToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisclosureTokenRepository extends JpaRepository<DisclosureToken, UUID> {
    Optional<DisclosureToken> findByTokenHash(String tokenHash);
    Optional<DisclosureToken> findByWillIdAndBeneficiaryId(UUID willId, UUID beneficiaryId);
    List<DisclosureToken> findByWillId(UUID willId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE DisclosureToken t SET t.status = com.digitalwill.release.model.DisclosureTokenStatus.ACCESSED, t.firstAccessedAt = :now, t.lastAccessedAt = :now, t.accessCount = t.accessCount + 1 WHERE t.id = :id AND t.status = com.digitalwill.release.model.DisclosureTokenStatus.ACTIVE")
    int consumeTokenIfActive(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}

