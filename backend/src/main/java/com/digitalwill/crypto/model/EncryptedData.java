package com.digitalwill.crypto.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Result of an envelope encryption operation.
 * Contains ciphertext (including GCM authentication tag), wrapped DEK, IV, and algorithm metadata.
 */
public record EncryptedData(
        byte[] ciphertext,
        String encryptedDek,
        String iv,
        String algorithm,
        String keyWrapAlgorithm,
        int version
) {
    public EncryptedData {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        Objects.requireNonNull(encryptedDek, "encryptedDek must not be null");
        Objects.requireNonNull(iv, "iv must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(keyWrapAlgorithm, "keyWrapAlgorithm must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncryptedData that)) return false;
        return version == that.version &&
                Arrays.equals(ciphertext, that.ciphertext) &&
                Objects.equals(encryptedDek, that.encryptedDek) &&
                Objects.equals(iv, that.iv) &&
                Objects.equals(algorithm, that.algorithm) &&
                Objects.equals(keyWrapAlgorithm, that.keyWrapAlgorithm);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(encryptedDek, iv, algorithm, keyWrapAlgorithm, version);
        result = 31 * result + Arrays.hashCode(ciphertext);
        return result;
    }

    @Override
    public String toString() {
        return "EncryptedData[" +
                "ciphertextLength=" + ciphertext.length +
                ", algorithm='" + algorithm + '\'' +
                ", keyWrapAlgorithm='" + keyWrapAlgorithm + '\'' +
                ", version=" + version +
                ']';
    }
}
