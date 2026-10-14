package com.digitalwill.document.validation;

import com.digitalwill.common.exception.DocumentValidationException;
import com.digitalwill.document.model.DocumentCategory;
import com.digitalwill.document.model.DocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Orchestrated server-side document validation pipeline executing Stages A through E.
 */
@Component
public class DocumentValidationPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidationPipeline.class);

    public static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024L; // 20 MB
    public static final int MAX_FILENAME_LENGTH = 255;

    private final MediaDetector mediaDetector;
    private final SafeDocumentParser safeDocumentParser;
    private final MalwareScanner malwareScanner;

    public DocumentValidationPipeline(
            MediaDetector mediaDetector,
            SafeDocumentParser safeDocumentParser,
            MalwareScanner malwareScanner
    ) {
        this.mediaDetector = Objects.requireNonNull(mediaDetector);
        this.safeDocumentParser = Objects.requireNonNull(safeDocumentParser);
        this.malwareScanner = Objects.requireNonNull(malwareScanner);
    }

    public record PipelineValidationResult(
            DocumentStatus status,
            String statusCode,
            String statusMessage,
            String detectedContentType,
            DocumentCategory category,
            String sanitizedFileName
    ) {}

    /**
     * Executes the authoritative validation pipeline on file bytes and metadata.
     */
    public PipelineValidationResult validate(
            String originalFileName,
            String clientContentType,
            DocumentCategory requestedCategory,
            byte[] fileBytes
    ) {
        // --- Stage A: Authentication, authorization, limits ---
        if (fileBytes == null || fileBytes.length == 0) {
            throw new DocumentValidationException("REQUIRED_FIELD_MISSING", "Uploaded file must not be empty");
        }

        if (fileBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new DocumentValidationException(
                    "FILE_TOO_LARGE",
                    "Uploaded file exceeds maximum limit of " + (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + "MB",
                    HttpStatus.PAYLOAD_TOO_LARGE
            );
        }

        if (originalFileName != null) {
            if (originalFileName.contains("..") || originalFileName.contains("/") || originalFileName.contains("\\")) {
                throw new DocumentValidationException("PATH_TRAVERSAL_DETECTED", "Filename contains path traversal characters");
            }
            if (originalFileName.chars().anyMatch(ch -> ch < 32 || ch == 127)) {
                throw new DocumentValidationException("MALFORMED_FILENAME", "Filename contains control characters");
            }
            if (originalFileName.length() > MAX_FILENAME_LENGTH) {
                throw new DocumentValidationException("INVALID_ARGUMENT", "Filename exceeds maximum length of " + MAX_FILENAME_LENGTH + " characters");
            }
        }

        String sanitizedFileName = sanitizeFileName(originalFileName);

        // --- Stage B: Content detection ---
        MediaDetector.DetectionResult detection = mediaDetector.detectAndVerify(sanitizedFileName, fileBytes);
        String authoritativeContentType = detection.detectedContentType();

        // --- Stage C: Safe parsing ---
        safeDocumentParser.parseAndVerify(authoritativeContentType, fileBytes);

        // --- Stage D: Malware scan ---
        ScanResult scan = malwareScanner.scan(fileBytes);
        if (scan.status() == ScanStatus.INFECTED) {
            log.warn("Uploaded file [{}] flagged as infected: {}", sanitizedFileName, scan.virusName());
            throw new DocumentValidationException(
                    "MALWARE_DETECTED",
                    "Security violation: malware signature detected (" + scan.virusName() + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        } else if (scan.status() == ScanStatus.ERROR) {
            if (malwareScanner instanceof ClamAvMalwareScanner clam && clam.isMandatory()) {
                log.error("Mandatory scanner is unavailable for file [{}]: {}", sanitizedFileName, scan.message());
                throw new DocumentValidationException(
                    "SCANNER_UNAVAILABLE",
                    "Antivirus service is unavailable and scanning is mandatory. Please retry later.",
                    HttpStatus.SERVICE_UNAVAILABLE
                );
            } else {
                log.warn("Scanner reported error in optional mode: {}", scan.message());
            }
        }

        // --- Stage E: Category/relevance check ---
        DocumentCategory category = requestedCategory != null ? requestedCategory : DocumentCategory.GENERAL;
        validateCategoryCompatibility(category, authoritativeContentType);

        return new PipelineValidationResult(
                DocumentStatus.ACCEPTED,
                "VALIDATED",
                "Document passed all security and integrity checks",
                authoritativeContentType,
                category,
                sanitizedFileName
        );
    }

    private void validateCategoryCompatibility(DocumentCategory category, String detectedContentType) {
        if (category == null || category == DocumentCategory.GENERAL) {
            return;
        }
        boolean isImageOrPdf = MediaDetector.MEDIA_TYPE_PDF.equals(detectedContentType)
                || MediaDetector.MEDIA_TYPE_JPEG.equals(detectedContentType)
                || MediaDetector.MEDIA_TYPE_PNG.equals(detectedContentType);
        boolean isDocx = MediaDetector.MEDIA_TYPE_DOCX.equals(detectedContentType);

        switch (category) {
            case IDENTITY_RECORD, PROPERTY_DEED, FINANCIAL_RECORD, MEDICAL_RECORD -> {
                if (!isImageOrPdf) {
                    throw new DocumentValidationException(
                            "INVALID_CATEGORY_FORMAT",
                            "Category " + category + " requires PDF or image format (JPEG/PNG). Detected format [" + detectedContentType + "] is not permitted for this category."
                    );
                }
            }
            case WILL_ATTACHMENT -> {
                if (!isImageOrPdf && !isDocx) {
                    throw new DocumentValidationException(
                            "INVALID_CATEGORY_FORMAT",
                            "Category WILL_ATTACHMENT requires PDF, DOCX, or image format."
                    );
                }
            }
            default -> {}
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document.bin";
        }
        // Strip path traversal attempts and control characters
        String clean = fileName.replaceAll("[/\\\\\\r\\n\\x00-\\x1f]", "_").trim();
        if (clean.length() > MAX_FILENAME_LENGTH) {
            // Preserve extension if possible
            int dot = clean.lastIndexOf('.');
            if (dot > 0 && dot > clean.length() - 10) {
                String ext = clean.substring(dot);
                clean = clean.substring(0, MAX_FILENAME_LENGTH - ext.length()) + ext;
            } else {
                clean = clean.substring(0, MAX_FILENAME_LENGTH);
            }
        }
        return clean;
    }
}
