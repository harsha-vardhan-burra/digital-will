package com.digitalwill.verification.repository;

import com.digitalwill.verification.model.VerificationRequest;
import com.digitalwill.verification.model.VerificationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, UUID> {

    Optional<VerificationRequest> findByTokenHash(String tokenHash);

    List<VerificationRequest> findByWillIdAndStatus(UUID willId, VerificationRequestStatus status);

    List<VerificationRequest> findByWillIdAndVerificationCycle(UUID willId, Long verificationCycle);

    @Modifying
    @Query("UPDATE VerificationRequest r SET r.status = 'REVOKED' WHERE r.willId = :willId AND r.status = 'ACTIVE'")
    int revokeActiveByWillId(@Param("willId") UUID willId);

    @Modifying
    @Query("UPDATE VerificationRequest r SET r.status = 'REVOKED' WHERE r.willId = :willId AND r.verificationCycle = :cycle AND r.status = 'ACTIVE'")
    int revokeActiveByWillIdAndCycle(@Param("willId") UUID willId, @Param("cycle") Long cycle);
}