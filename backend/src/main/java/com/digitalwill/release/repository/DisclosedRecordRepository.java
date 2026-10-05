package com.digitalwill.release.repository;

import com.digitalwill.release.model.DisclosedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisclosedRecordRepository extends JpaRepository<DisclosedRecord, UUID> {
    Optional<DisclosedRecord> findByWillIdAndBeneficiaryId(UUID willId, UUID beneficiaryId);
    Optional<DisclosedRecord> findByDisclosureTokenId(UUID disclosureTokenId);
    List<DisclosedRecord> findByWillId(UUID willId);
}
