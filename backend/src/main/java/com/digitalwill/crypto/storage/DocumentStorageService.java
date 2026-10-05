package com.digitalwill.crypto.storage;

import java.util.UUID;

/**
 * Storage service abstraction for encrypted payloads.
 * Decouples file persistence mechanism from domain business logic.
 */
public interface DocumentStorageService {

    /**
     * Stores encrypted payload for the specified will and document ID.
     *
     * @param willId ID of the will owning the document
     * @param documentId unique document identifier
     * @param encryptedBytes encrypted ciphertext payload
     * @return storage path or reference identifier
     */
    String store(UUID willId, UUID documentId, byte[] encryptedBytes);

    /**
     * Retrieves encrypted payload by storage path reference.
     *
     * @param storagePath reference returned by {@link #store}
     * @return encrypted bytes
     */
    byte[] retrieve(String storagePath);

    /**
     * Deletes stored payload.
     *
     * @param storagePath reference to delete
     */
    void delete(String storagePath);
}
