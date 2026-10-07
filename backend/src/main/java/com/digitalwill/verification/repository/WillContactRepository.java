package com.digitalwill.verification.repository;

import com.digitalwill.verification.model.WillContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WillContactRepository extends JpaRepository<WillContact, UUID> {
    List<WillContact> findByWillId(UUID willId);
    List<WillContact> findByWillIdAndActiveTrue(UUID willId);
    long countByWillIdAndActiveTrue(UUID willId);
    Optional<WillContact> findByWillIdAndContactId(UUID willId, UUID contactId);
    boolean existsByWillIdAndContactId(UUID willId, UUID contactId);
}