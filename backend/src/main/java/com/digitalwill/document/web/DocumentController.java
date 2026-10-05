package com.digitalwill.document.web;

import com.digitalwill.document.exception.DocumentNotFoundException;
import com.digitalwill.document.model.EncryptedDocument;
import com.digitalwill.document.repository.EncryptedDocumentRepository;
import com.digitalwill.document.service.DocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public DocumentController(DocumentService documentService,
                              EncryptedDocumentRepository documentRepository) {
        this.documentService = Objects.requireNonNull(documentService);
        this.documentRepository = Objects.requireNonNull(documentRepository);
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
            @RequestParam(value = "ownerId", required = false) UUID ownerId
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.bin";
        String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        EncryptedDocument saved = documentService.uploadDocument(
                willId,
                ownerId,
                fileName,
                contentType,
                file.getBytes()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.fromEntity(saved));
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<DocumentResponse> getMetadata(
            @PathVariable UUID id,
            @RequestParam(value = "willId", required = false) UUID willId
    ) {
        EncryptedDocument doc = (willId != null)
                ? documentService.getDocumentMetadata(willId, id)
                : documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + id));
        return ResponseEntity.ok(DocumentResponse.fromEntity(doc));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID id,
            @RequestParam(value = "willId", required = false) UUID willId,
            @RequestParam(value = "actorId", required = false, defaultValue = "ANONYMOUS") String actorId,
            @RequestParam(value = "actorType", required = false, defaultValue = "OWNER") String actorType
    ) {
        EncryptedDocument doc = (willId != null)
                ? documentService.getDocumentMetadata(willId, id)
                : documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + id));

        byte[] decryptedBytes = documentService.downloadDocument(doc.getWillId(), id, actorId, actorType);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, doc.getContentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(decryptedBytes.length))
                .body(decryptedBytes);
    }

    @GetMapping("/will/{willId}")
    public ResponseEntity<List<DocumentResponse>> listDocuments(@PathVariable UUID willId) {
        List<DocumentResponse> docs = documentService.listDocumentsForWill(willId).stream()
                .map(DocumentResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(docs);
    }
}
