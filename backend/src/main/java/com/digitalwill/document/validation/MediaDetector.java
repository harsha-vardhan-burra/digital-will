package com.digitalwill.document.validation;

import com.digitalwill.common.exception.DocumentValidationException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stage B: Content detection using Apache Tika byte inspection.
 * Compares detected media type against allowlist and checks extension-content alignment.
 */
@Component
public class MediaDetector {

    private final Tika tika = new Tika();

    public static final String MEDIA_TYPE_PDF = "application/pdf";
    public static final String MEDIA_TYPE_JPEG = "image/jpeg";
    public static final String MEDIA_TYPE_PNG = "image/png";
    public static final String MEDIA_TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MEDIA_TYPE_ZIP = "application/zip";

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            MEDIA_TYPE_PDF,
            MEDIA_TYPE_JPEG,
            MEDIA_TYPE_PNG,
            MEDIA_TYPE_DOCX
    );

    private static final Map<String, Set<String>> EXTENSION_TO_MEDIA_TYPES = Map.of(
            "pdf", Set.of(MEDIA_TYPE_PDF),
            "jpg", Set.of(MEDIA_TYPE_JPEG),
            "jpeg", Set.of(MEDIA_TYPE_JPEG),
            "png", Set.of(MEDIA_TYPE_PNG),
            "docx", Set.of(MEDIA_TYPE_DOCX, MEDIA_TYPE_ZIP)
    );

    public record DetectionResult(
            String detectedContentType,
            String normalizedExtension
    ) {}

    /**
     * Inspects raw file bytes using Apache Tika to determine authoritative media type.
     * Rejects disallowed types and extension/content mismatches.
     */
    public DetectionResult detectAndVerify(String originalFileName, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new DocumentValidationException("MALFORMED_DOCUMENT", "File is empty or null");
        }

        String extension = extractExtension(originalFileName);
        if (extension == null || !EXTENSION_TO_MEDIA_TYPES.containsKey(extension)) {
            throw new DocumentValidationException(
                    "UNSUPPORTED_FILE_TYPE",
                    "Unsupported file extension. Permitted formats are: PDF, JPEG, PNG, DOCX."
            );
        }

        String detectedType;
        try {
            detectedType = tika.detect(new ByteArrayInputStream(fileBytes), originalFileName);
        } catch (IOException e) {
            throw new DocumentValidationException("MALFORMED_DOCUMENT", "Unable to detect file media type: " + e.getMessage());
        }

        if (detectedType == null) {
            throw new DocumentValidationException("UNSUPPORTED_FILE_TYPE", "Unable to determine content type from file bytes");
        }

        // Normalize charset suffixes if present (e.g. image/jpeg; charset=...)
        if (detectedType.contains(";")) {
            detectedType = detectedType.split(";")[0].trim();
        }

        // Map generic ZIP with .docx extension if DOCX magic detected
        if (MEDIA_TYPE_ZIP.equals(detectedType) && "docx".equals(extension)) {
            detectedType = MEDIA_TYPE_DOCX;
        }

        if (!ALLOWED_MEDIA_TYPES.contains(detectedType)) {
            throw new DocumentValidationException(
                    "UNSUPPORTED_FILE_TYPE",
                    "Detected content type [" + detectedType + "] is not permitted. Only PDF, JPEG, PNG, and DOCX are allowed."
            );
        }

        // Validate extension matches detected content
        Set<String> validTypesForExt = EXTENSION_TO_MEDIA_TYPES.get(extension);
        if (validTypesForExt == null || !validTypesForExt.contains(detectedType)) {
            throw new DocumentValidationException(
                    "CONTENT_TYPE_MISMATCH",
                    "Extension '." + extension + "' does not match detected content type [" + detectedType + "]"
            );
        }

        return new DetectionResult(detectedType, extension);
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT).trim();
    }
}
