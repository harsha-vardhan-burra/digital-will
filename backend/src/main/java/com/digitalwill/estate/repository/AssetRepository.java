package com.digitalwill.estate.repository;

import com.digitalwill.estate.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByWillId(UUID willId);
    Optional<Asset> findByIdAndWillId(UUID id, UUID willId);
    long countByWillId(UUID willId);
}
