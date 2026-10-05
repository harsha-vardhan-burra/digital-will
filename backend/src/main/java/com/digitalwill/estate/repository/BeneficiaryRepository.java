package com.digitalwill.estate.repository;

import com.digitalwill.estate.model.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByWillId(UUID willId);
    Optional<Beneficiary> findByIdAndWillId(UUID id, UUID willId);
    long countByWillId(UUID willId);
}
