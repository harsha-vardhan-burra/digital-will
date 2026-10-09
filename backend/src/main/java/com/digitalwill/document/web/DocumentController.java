package com.digitalwill.document.web;

import com.digitalwill.auth.security.UserPrincipal;
import com.digitalwill.document.exception.DocumentNotFoundException;
import com.digitalwill.document.model.EncryptedDocument;
import com.digitalwill.document.repository.EncryptedDocumentRepository;
import com.digitalwill.document.service.DocumentService;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final EncryptedDocumentRepository documentRepository;
    private final WillStateRepository willStateRepository;

    public DocumentController(DocumentService documentService,
                              EncryptedDocumentRepository documentRepository,
                              WillStateRepository willStateRepository) {
        this.documentService = Objects.requireNonNull(documentService);
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
    }

    private void checkWillOwnership(UUID willId, UserPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));
        if (!will.getOwnerId().equals(principal.getId())) {
            throw new SecurityException("Unauthorized: actor is not the owner of Will: " + willId);
        }
    }

    private static String sanitizeHeaderFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document.bin";
        }
        return fileName.replaceAll("[\"\\r\\n\\x00-\\x1f]", "_");
    }

    public record DocumentResponse(
            UUID id,
            UUID willId,
            String fileName,
            String contentType,
            long fileSize,
            String checksumSha256,
            Instant createdAt
    ) {
        public static DocumentResponse fromEntity(EncryptedDocument doc) {
            return new DocumentResponse(
                    doc.getId(),
                    doc.getWillId(),
                    doc.getFileName(),
                    doc.getContentType(),
                    doc.getFileSize(),
                    doc.getChecksumSha256(),
                    doc.getCreatedAt()
            );
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("willId") UUID willId,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        checkWillOwnership(willId, principal);

        UUID effectiveOwnerId = principal.getId();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.bin";
        String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        EncryptedDocument saved = documentService.uploadDocument(
                willId,
                effectiveOwnerId,
                fileName,
                contentType,
                file.getBytes()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.fromEntity(saved));
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<DocumentResponse> getMetadata(
            @PathVariable UUID id,
            @RequestParam(value = "willId", required = false) UUID willId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        EncryptedDocument doc = (willId != null)
                ? documentService.getDocumentMetadata(willId, id)
                : documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + id));
        checkWillOwnership(doc.getWillId(), principal);
        return ResponseEntity.ok(DocumentResponse.fromEntity(doc));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID id,
            @RequestParam(value = "willId", required = false) UUID willId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        EncryptedDocument doc = (willId != null)
                ? documentService.getDocumentMetadata(willId, id)
                : documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + id));
        checkWillOwnership(doc.getWillId(), principal);

        String effectiveActorId = principal.getId().toString();
        byte[] decryptedBytes = documentService.downloadDocument(doc.getWillId(), id, effectiveActorId, "OWNER");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeHeaderFileName(doc.getFileName()) + "\"")
                .header(HttpHeaders.CONTENT_TYPE, doc.getContentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(decryptedBytes.length))
                .body(decryptedBytes);
    }

    @GetMapping("/will/{willId}")
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @PathVariable UUID willId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        checkWillOwnership(willId, principal);
        List<DocumentResponse> docs = documentService.listDocumentsForWill(willId).stream()
                .map(DocumentResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(docs);
    }
}
