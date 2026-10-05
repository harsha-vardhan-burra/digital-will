package com.digitalwill.crypto.storage;

import com.digitalwill.crypto.config.CryptoProperties;
import com.digitalwill.crypto.exception.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Local filesystem implementation of DocumentStorageService.
 * Stores encrypted document bytes on disk without exposing plaintext.
 */
@Service
public class LocalDocumentStorageService implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorageService.class);

    private final Path rootStorageDir;

    public LocalDocumentStorageService(CryptoProperties cryptoProperties) {
        Objects.requireNonNull(cryptoProperties, "cryptoProperties must not be null");
        this.rootStorageDir = cryptoProperties.getResolvedStoragePath();
        try {
            Files.createDirectories(this.rootStorageDir);
        } catch (IOException e) {
            throw new CryptoException("Failed to initialize document storage directory: " + rootStorageDir, e);
        }
    }

    @Override
    public String store(UUID willId, UUID documentId, byte[] encryptedBytes) {
        Objects.requireNonNull(willId, "willId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(encryptedBytes, "encryptedBytes must not be null");

        Path willDir = rootStorageDir.resolve(willId.toString()).normalize();
        validatePathUnderRoot(willDir);

        try {
            Files.createDirectories(willDir);
            String fileName = documentId + ".enc";
            Path filePath = willDir.resolve(fileName).normalize();
            validatePathUnderRoot(filePath);

            Files.write(filePath, encryptedBytes);
            log.debug("Stored encrypted document [{}] at [{}]", documentId, filePath);

            // Relative storage reference
            return willId + "/" + fileName;
        } catch (IOException e) {
            log.error("Failed to store encrypted document [{}] for will [{}]: {}", documentId, willId, e.getMessage());
            throw new CryptoException("Failed to store encrypted document: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] retrieve(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        if (storagePath.contains("..") || storagePath.contains(":") || storagePath.startsWith("/") || storagePath.startsWith("\\")) {
            throw new SecurityException("Path traversal attempt detected: invalid storage path");
        }
        Path targetPath = rootStorageDir.resolve(storagePath).normalize();
        validatePathUnderRoot(targetPath);

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new CryptoException("Encrypted document file not found");
        }

        try {
            return Files.readAllBytes(targetPath);
        } catch (IOException e) {
            log.error("Failed to read encrypted document: {}", e.getMessage());
            throw new CryptoException("Failed to read encrypted document", e);
        }
    }

    @Override
    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        if (storagePath.contains("..") || storagePath.contains(":") || storagePath.startsWith("/") || storagePath.startsWith("\\")) {
            throw new SecurityException("Path traversal attempt detected: invalid storage path");
        }
        Path targetPath = rootStorageDir.resolve(storagePath).normalize();
        validatePathUnderRoot(targetPath);

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            log.warn("Failed to delete encrypted document file: {}", e.getMessage());
        }
    }

    private void validatePathUnderRoot(Path target) {
        if (!target.startsWith(rootStorageDir)) {
            throw new SecurityException("Path traversal attempt detected: invalid storage path");
        }
    }
}
