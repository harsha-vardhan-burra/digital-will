package com.digitalwill.document.validation;

import com.digitalwill.common.exception.DocumentValidationException;
import com.digitalwill.document.model.DocumentCategory;
import com.digitalwill.document.model.DocumentStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentValidationPipelineTest {

    private MediaDetector mediaDetector;
    private SafeDocumentParser safeDocumentParser;
    private ClamAvMalwareScanner optionalScanner;
    private ClamAvMalwareScanner mandatoryScanner;

    private DocumentValidationPipeline pipeline;

    @BeforeEach
    void setUp() {
        mediaDetector = new MediaDetector();
        safeDocumentParser = new SafeDocumentParser();
        // Optional scanner on dummy port for default tests
        optionalScanner = new ClamAvMalwareScanner(false, "localhost", 3310, 5000, "OPTIONAL");
        // Mandatory scanner pointing to unavailable port for fail-closed tests
        mandatoryScanner = new ClamAvMalwareScanner(true, "127.0.0.1", 65534, 1000, "MANDATORY");

        pipeline = new DocumentValidationPipeline(mediaDetector, safeDocumentParser, optionalScanner);
    }

    private byte[] createMinimalPdf() {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createPasswordProtectedPdf() {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            StandardProtectionPolicy spp = new StandardProtectionPolicy("secretOwnerPass", "secretUserPass", new AccessPermission());
            doc.protect(spp);
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createMinimalPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAA=");
    }

    private byte[] createMinimalDocx() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Hello</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createMacroDocx() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("word/vbaProject.bin"));
            zos.write("MACRO_BINARY_DATA".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createZipTraversalDocx() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("../../evil.txt"));
            zos.write("traversal payload".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Stage A-E: Valid PDF passes pipeline with ACCEPTED status and sanitized filename")
    void validPdfPassesPipeline() {
        byte[] pdfBytes = createMinimalPdf();
        var result = pipeline.validate(
                "My Will & Testament (Final).pdf",
                "application/pdf",
                DocumentCategory.WILL_ATTACHMENT,
                pdfBytes
        );

        assertThat(result.status()).isEqualTo(DocumentStatus.ACCEPTED);
        assertThat(result.statusCode()).isEqualTo("VALIDATED");
        assertThat(result.detectedContentType()).isEqualTo("application/pdf");
        assertThat(result.sanitizedFileName()).isEqualTo("My Will & Testament (Final).pdf");
    }

    @Test
    @DisplayName("Stage A-E: Valid PNG passes pipeline with ACCEPTED status")
    void validPngPassesPipeline() {
        byte[] pngBytes = createMinimalPng();
        var result = pipeline.validate(
                "passport_photo.png",
                "image/png",
                DocumentCategory.IDENTITY_RECORD,
                pngBytes
        );

        assertThat(result.status()).isEqualTo(DocumentStatus.ACCEPTED);
        assertThat(result.detectedContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Stage A-E: Valid DOCX passes safe archive parsing and passes pipeline")
    void validDocxPassesPipeline() {
        byte[] docxBytes = createMinimalDocx();
        var result = pipeline.validate(
                "attachment_notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DocumentCategory.WILL_ATTACHMENT,
                docxBytes
        );

        assertThat(result.status()).isEqualTo(DocumentStatus.ACCEPTED);
        assertThat(result.detectedContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    @DisplayName("Stage E: DOCX uploaded as PROPERTY_DEED or IDENTITY_RECORD is rejected with INVALID_CATEGORY_FORMAT")
    void docxIncompatibleWithDeedOrIdentityIsRejected() {
        byte[] docxBytes = createMinimalDocx();
        assertThatThrownBy(() -> pipeline.validate(
                "deed.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DocumentCategory.PROPERTY_DEED,
                docxBytes
        )).isInstanceOf(DocumentValidationException.class)
          .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("INVALID_CATEGORY_FORMAT"));

        assertThatThrownBy(() -> pipeline.validate(
                "passport.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DocumentCategory.IDENTITY_RECORD,
                docxBytes
        )).isInstanceOf(DocumentValidationException.class)
          .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("INVALID_CATEGORY_FORMAT"));
    }

    @Test
    @DisplayName("Stage A: Empty file upload is rejected with REQUIRED_FIELD_MISSING")
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> pipeline.validate("empty.pdf", "application/pdf", DocumentCategory.GENERAL, new byte[0]))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("REQUIRED_FIELD_MISSING"));
    }

    @Test
    @DisplayName("Stage A: Oversized file exceeding 20MB is rejected with FILE_TOO_LARGE")
    void oversizedFileIsRejected() {
        byte[] oversizedBytes = new byte[21 * 1024 * 1024]; // 21 MB
        assertThatThrownBy(() -> pipeline.validate("huge.pdf", "application/pdf", DocumentCategory.GENERAL, oversizedBytes))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("FILE_TOO_LARGE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../etc/passwd",
            "..\\..\\windows\\system32\\cmd.exe",
            "evil\0name.pdf",
            "bad\nnewline.pdf",
            "bad\rname.pdf"
    })
    @DisplayName("Stage A: Path traversal and control characters in filename are rejected")
    void pathTraversalAndControlCharsInFilenameRejected(String dangerousFilename) {
        assertThatThrownBy(() -> pipeline.validate(dangerousFilename, "application/pdf", DocumentCategory.GENERAL, createMinimalPdf()))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isIn("MALFORMED_FILENAME", "PATH_TRAVERSAL_DETECTED"));
    }

    @Test
    @DisplayName("Stage B: Disallowed media type (.exe, .sh, .html) is rejected")
    void unsupportedFileTypesAreRejected() {
        byte[] scriptBytes = "#!/bin/bash\necho dangerous\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> pipeline.validate("script.sh", "application/x-sh", DocumentCategory.GENERAL, scriptBytes))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    @DisplayName("Stage B: Content type / extension mismatch is rejected")
    void extensionContentMismatchRejected() {
        byte[] pngBytes = createMinimalPng();
        assertThatThrownBy(() -> pipeline.validate("fake.pdf", "application/pdf", DocumentCategory.GENERAL, pngBytes))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("CONTENT_TYPE_MISMATCH"));
    }

    @Test
    @DisplayName("Stage C: Password-protected PDF is rejected safely")
    void passwordProtectedPdfIsRejected() {
        byte[] encryptedPdf = createPasswordProtectedPdf();
        assertThatThrownBy(() -> pipeline.validate("locked.pdf", "application/pdf", DocumentCategory.GENERAL, encryptedPdf))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("DOCUMENT_PASSWORD_PROTECTED"));
    }

    @Test
    @DisplayName("Stage C: Malformed/corrupted PDF is rejected safely")
    void malformedPdfIsRejected() {
        byte[] corruptPdf = "%PDF-1.4\ncorrupted trailing truncated garbage".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> pipeline.validate("corrupt.pdf", "application/pdf", DocumentCategory.GENERAL, corruptPdf))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isIn("DOCUMENT_CORRUPT", "CONTENT_TYPE_MISMATCH"));
    }

    @Test
    @DisplayName("Stage C: DOCX with embedded macros (vbaProject.bin) is rejected safely")
    void docxWithMacrosIsRejected() {
        byte[] macroDocx = createMacroDocx();
        assertThatThrownBy(() -> pipeline.validate("macro.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocumentCategory.GENERAL, macroDocx))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isIn("DOCUMENT_ACTIVE_CONTENT_PROHIBITED", "DOCUMENT_CORRUPT", "UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    @DisplayName("Stage C: DOCX with zip path traversal entry is rejected safely")
    void docxWithZipTraversalEntryRejected() {
        byte[] zipTraversalDocx = createZipTraversalDocx();
        assertThatThrownBy(() -> pipeline.validate("traversal.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocumentCategory.GENERAL, zipTraversalDocx))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isIn("DOCUMENT_CORRUPT", "PATH_TRAVERSAL_DETECTED", "UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    @DisplayName("Stage D: EICAR test string is detected as malware and rejected with MALWARE_DETECTED")
    void eicarMalwareDetectedAndRejected() {
        // Embed EICAR test string inside a document byte payload
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        byte[] infectedFile = eicar.getBytes(StandardCharsets.UTF_8);

        // ClamAvMalwareScanner detects standard EICAR test signature directly in test double/adapter mode
        ClamAvMalwareScanner scannerWithEicar = new ClamAvMalwareScanner(true, "localhost", 3310, 5000, "MANDATORY");
        var scanRes = scannerWithEicar.scan(infectedFile);
        assertThat(scanRes.status()).isEqualTo(ScanStatus.INFECTED);
        assertThat(scanRes.virusName()).containsIgnoringCase("EICAR");
    }

    @Test
    @DisplayName("Stage D: Scanner outage in MANDATORY mode fails closed with SCANNER_UNAVAILABLE")
    void scannerOutageInMandatoryModeFailsClosed() {
        byte[] pdfBytes = createMinimalPdf();
        DocumentValidationPipeline mandatoryPipeline = new DocumentValidationPipeline(
                mediaDetector, safeDocumentParser, mandatoryScanner
        );

        assertThatThrownBy(() -> mandatoryPipeline.validate("test.pdf", "application/pdf", DocumentCategory.GENERAL, pdfBytes))
                .isInstanceOf(DocumentValidationException.class)
                .satisfies(e -> assertThat(((DocumentValidationException) e).getCode()).isEqualTo("SCANNER_UNAVAILABLE"));
    }
}
