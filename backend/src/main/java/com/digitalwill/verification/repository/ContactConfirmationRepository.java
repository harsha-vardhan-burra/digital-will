package com.digitalwill.verification.repository;

import com.digitalwill.verification.model.ContactConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactConfirmationRepository extends JpaRepository<ContactConfirmation, UUID> {

    List<ContactConfirmation> findByWillIdAndVerificationCycle(UUID willId, Long verificationCycle);

    Optional<ContactConfirmation> findByWillIdAndContactIdAndVerificationCycle(UUID willId, UUID contactId, Long verificationCycle);

    boolean existsByWillIdAndContactIdAndVerificationCycle(UUID willId, UUID contactId, Long verificationCycle);

    @Query("SELECT COUNT(DISTINCT c.contactId) FROM ContactConfirmation c WHERE c.willId = :willId AND c.verificationCycle = :cycle")
    long countDistinctContactsByWillIdAndCycle(@Param("willId") UUID willId, @Param("cycle") Long cycle);

    @Query("SELECT COUNT(c) FROM ContactConfirmation c WHERE c.willId = :willId AND c.verificationCycle = :cycle")
    long countByWillIdAndCycle(@Param("willId") UUID willId, @Param("cycle") Long cycle);
}