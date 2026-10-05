package com.digitalwill.document.repository;

import com.digitalwill.document.model.EncryptedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncryptedDocumentRepository extends JpaRepository<EncryptedDocument, UUID> {

    List<EncryptedDocument> findByWillId(UUID willId);

    Optional<EncryptedDocument> findByIdAndWillId(UUID id, UUID willId);

    long countByWillId(UUID willId);
}
