package com.digitalwill.release.web;

import com.digitalwill.document.model.EncryptedDocument;
import com.digitalwill.document.repository.EncryptedDocumentRepository;
import com.digitalwill.document.service.DocumentService;
import com.digitalwill.release.service.DisclosureService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/disclosure")
public class DisclosureController {

    private final DisclosureService disclosureService;
    private final DocumentService documentService;
    private final EncryptedDocumentRepository documentRepository;

    public DisclosureController(DisclosureService disclosureService,
                                DocumentService documentService,
                                EncryptedDocumentRepository documentRepository) {
        this.disclosureService = Objects.requireNonNull(disclosureService);
        this.documentService = Objects.requireNonNull(documentService);
        this.documentRepository = Objects.requireNonNull(documentRepository);
    }

    public record DisclosureResponse(
            UUID willId,
            UUID beneficiaryId,
            String packagePayloadJson,
            Instant accessedAt
    ) {
        public static DisclosureResponse fromService(DisclosureService.BeneficiaryDisclosure b) {
            return new DisclosureResponse(b.willId(), b.beneficiaryId(), b.payloadJson(), b.accessedAt());
        }
    }

    @GetMapping("/{token}")
    public ResponseEntity<DisclosureResponse> getDisclosure(@PathVariable String token) {
        DisclosureService.BeneficiaryDisclosure disclosure = disclosureService.accessDisclosure(token);
        return ResponseEntity.ok(DisclosureResponse.fromService(disclosure));
    }

    @GetMapping("/{token}/document/{documentId}")
    public ResponseEntity<byte[]> downloadDisclosedDocument(
            @PathVariable String token,
            @PathVariable UUID documentId
    ) {
        DisclosureService.BeneficiaryDisclosure disclosure = disclosureService.accessDisclosure(token);

        EncryptedDocument doc = documentRepository.findByIdAndWillId(documentId, disclosure.willId())
                .orElseThrow(() -> new com.digitalwill.document.exception.DocumentNotFoundException("Document does not belong to this estate package"));

        // Server-side authorization check: verify document is within beneficiary's disclosure scope
        if (!disclosure.payloadJson().contains(documentId.toString())) {
            throw new SecurityException("Unauthorized: document " + documentId + " is outside the disclosure scope for beneficiary " + disclosure.beneficiaryId());
        }

        byte[] decrypted = documentService.downloadDocument(
                disclosure.willId(),
                documentId,
                disclosure.beneficiaryId().toString(),
                "BENEFICIARY"
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeHeaderFileName(doc.getFileName()) + "\"")
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(decrypted.length))
                .body(decrypted);
    }

    private static String sanitizeHeaderFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document.bin";
        }
        return fileName.replaceAll("[\"\\r\\n\\x00-\\x1f]", "_");
    }
}
