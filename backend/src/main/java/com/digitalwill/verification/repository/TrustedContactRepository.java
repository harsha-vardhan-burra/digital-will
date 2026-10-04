package com.digitalwill.verification.repository;

import com.digitalwill.verification.model.TrustedContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrustedContactRepository extends JpaRepository<TrustedContact, UUID> {
    Optional<TrustedContact> findByEmail(String email);
}