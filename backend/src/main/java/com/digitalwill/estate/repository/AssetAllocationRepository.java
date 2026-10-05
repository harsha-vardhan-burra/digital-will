package com.digitalwill.estate.repository;

import com.digitalwill.estate.model.AssetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetAllocationRepository extends JpaRepository<AssetAllocation, UUID> {
    List<AssetAllocation> findByAssetId(UUID assetId);
    List<AssetAllocation> findByBeneficiaryId(UUID beneficiaryId);
    Optional<AssetAllocation> findByAssetIdAndBeneficiaryId(UUID assetId, UUID beneficiaryId);

    @Query("SELECT a FROM AssetAllocation a JOIN Asset ast ON a.assetId = ast.id WHERE ast.willId = :willId")
    List<AssetAllocation> findByWillId(UUID willId);
}
