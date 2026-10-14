package com.digitalwill.security;

import com.digitalwill.auth.service.AuthService;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.document.model.DocumentCategory;
import com.digitalwill.document.model.DocumentStatus;
import com.digitalwill.document.model.EncryptedDocument;
import com.digitalwill.document.repository.EncryptedDocumentRepository;
import com.digitalwill.document.service.DocumentService;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.repository.AssetAllocationRepository;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adversarial regression test suite validating security boundaries, IDOR resistance,
 * estate mutability locks, prohibited credential screening, URL SSRF defenses,
 * Stage E document format compatibility, and internal job authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
public class AdversarialValidationSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired WillStateService willStateService;
    @Autowired WillStateRepository willStateRepository;
    @Autowired EstateService estateService;
    @Autowired DocumentService documentService;
    @Autowired EncryptedDocumentRepository documentRepository;
    @Autowired AssetAllocationRepository allocationRepository;
    @Autowired TestTimeProvider timeProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant baseTime = Instant.parse("2026-10-01T12:00:00Z");

    private String userAToken;
    private UUID userAId;
    private UUID willAId;

    private String userBToken;
    private UUID userBId;
    private UUID willBId;

    private UUID assetAId;
    private UUID beneficiaryAId;
    private UUID allocationAId;
    private UUID docAId;

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
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Sample</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        timeProvider.setNow(baseTime);

        // Register User A
        String emailA = "victim_" + UUID.randomUUID() + "@example.com";
        var authA = authService.register(emailA, "PasswordA123!", "Victim Alpha");
        userAToken = authA.token();
        userAId = authA.userId();

        WillStateEntity willA = willStateService.createWill(userAId, "Alpha Estate");
        willAId = willA.getId();

        Asset assetA = estateService.addAsset(willAId, "Primary Residence", AssetCategory.REAL_ESTATE,
                "123 Main St, Seattle, WA", null, "Key under mat",
                null, null, "USD", BigDecimal.valueOf(850000), LocalDate.now().minusDays(1),
                BigDecimal.valueOf(100), 2015, "https://secure.realty.example/deed", "PARCEL-1234");
        assetAId = assetA.getId();

        Beneficiary benA = estateService.addBeneficiary(willAId, "Alice Alpha", "alice@example.com", "Daughter");
        beneficiaryAId = benA.getId();

        var allocA = estateService.allocateAsset(willAId, assetAId, beneficiaryAId, 100, "Full transfer");
        allocationAId = allocA.getId();

        var docA = documentService.uploadDocument(willAId, userAId, "deed.pdf", "application/pdf", DocumentCategory.PROPERTY_DEED, createMinimalPdf());
        docAId = docA.getId();

        // Register User B (Adversary)
        String emailB = "attacker_" + UUID.randomUUID() + "@example.com";
        var authB = authService.register(emailB, "PasswordB123!", "Attacker Beta");
        userBToken = authB.token();
        userBId = authB.userId();

        WillStateEntity willB = willStateService.createWill(userBId, "Beta Estate");
        willBId = willB.getId();
    }

    // =========================================================================
    // 1. Principal Spoofing & Cross-Will IDOR Resistance
    // =========================================================================

    @Test
    @DisplayName("ADV-01: Cross-user mutation attempts are blocked with 403 Forbidden")
    void crossUserMutationBlocked() throws Exception {
        // Attacker B attempts to add an asset to Victim A's will
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Injected Asset",
                                "category", "OTHER"
                        ))))
                .andExpect(status().isForbidden());

        // Attacker B attempts to delete Victim A's asset
        mockMvc.perform(delete("/api/wills/" + willAId + "/assets/" + assetAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());

        // Attacker B attempts to add beneficiary to Victim A's will
        mockMvc.perform(post("/api/wills/" + willAId + "/beneficiaries")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Injected Beneficiary",
                                "email", "injected@example.com",
                                "relationship", "Friend"
                        ))))
                .andExpect(status().isForbidden());

        // Attacker B attempts to delete Victim A's beneficiary
        mockMvc.perform(delete("/api/wills/" + willAId + "/beneficiaries/" + beneficiaryAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());

        // Attacker B attempts to upload document to Victim A's will
        MockMultipartFile file = new MockMultipartFile("file", "evil.pdf", "application/pdf", createMinimalPdf());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willAId.toString())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());

        // Attacker B attempts to download Victim A's document
        mockMvc.perform(get("/api/documents/" + docAId + "/download?willId=" + willAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. Estate Mutability Locks (EXECUTING and EXECUTED States)
    // =========================================================================

    @Test
    @DisplayName("ADV-02: Executing will rejects all estate mutations with 409 Conflict")
    void executingWillRejectsAllMutations() throws Exception {
        WillStateEntity will = willStateRepository.findById(willAId).orElseThrow();
        will.setState(WillState.EXECUTING);
        willStateRepository.save(will);

        // 1. Add Asset -> 409 Conflict
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Post-Execution Asset",
                                "category", "OTHER"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 2. Delete Asset -> 409 Conflict
        mockMvc.perform(delete("/api/wills/" + willAId + "/assets/" + assetAId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 3. Add Beneficiary -> 409 Conflict
        mockMvc.perform(post("/api/wills/" + willAId + "/beneficiaries")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Heir",
                                "email", "newheir@example.com",
                                "relationship", "Friend"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 4. Delete Beneficiary -> 409 Conflict
        mockMvc.perform(delete("/api/wills/" + willAId + "/beneficiaries/" + beneficiaryAId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 5. Delete Allocation -> 409 Conflict
        mockMvc.perform(delete("/api/wills/" + willAId + "/allocations/" + allocationAId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 6. Upload Document -> 409 Conflict
        MockMultipartFile file = new MockMultipartFile("file", "post_exec.pdf", "application/pdf", createMinimalPdf());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willAId.toString())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("ADV-02b: Executed will rejects all estate mutations with 409 Conflict")
    void executedWillRejectsAllMutations() throws Exception {
        WillStateEntity will = willStateRepository.findById(willAId).orElseThrow();
        will.setState(WillState.EXECUTED);
        willStateRepository.save(will);

        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Terminal Asset",
                                "category", "OTHER"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // =========================================================================
    // 3. Prohibited Credential Screening
    // =========================================================================

    static java.util.stream.Stream<String> prohibitedCredentials() {
        return java.util.stream.Stream.of(
                "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0\n-----END RSA PRIVATE KEY-----",
                "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEI\n-----END EC PRIVATE KEY-----",
                "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNza\n-----END OPENSSH PRIVATE KEY-----",
                "-----BEGIN PGP PRIVATE KEY BLOCK-----\nlQH+BF...\n-----END PGP PRIVATE KEY BLOCK-----",
                "My 12-word seed phrase: apple banana cherry dog elephant fox grape horse igloo jaguar kite lemon",
                "password: SuperSecretPassword123!",
                "ATM PIN: 4321",
                "CVV: 789",
                "AKIAIOSFODNN7EXAMPLE",
                "ghp_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                String.join("-", "xoxb", "123456789012", "1234567890123", "abcdefghijklmnopqrstuvwxyz"),
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
                "otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        );
    }

    @ParameterizedTest
    @MethodSource("prohibitedCredentials")
    @DisplayName("ADV-03: Prohibited credentials in asset text fields or identifiers are rejected")
    void prohibitedCredentialsRejected(String prohibitedPayload) throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Test Asset",
                                "category", "DIGITAL_ACCOUNT",
                                "description", prohibitedPayload
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_STORAGE_PROHIBITED"));

        // Also verify screening in rawIdentifier
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Test Asset With Identifier",
                                "category", "BANK_ACCOUNT",
                                "rawIdentifier", prohibitedPayload
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_STORAGE_PROHIBITED"));
    }

    // =========================================================================
    // 4. URL Validation & SSRF Prevention
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",
            "http://127.0.0.1:8080/admin",
            "http://localhost/secret",
            "http://localhost./secret",
            "http://[::1]/secret",
            "http://[fe80::1]/secret",
            "http://[::ffff:127.0.0.1]/secret",
            "http://user:pass@example.com/vault",
            "http://2130706433/",
            "ftp://example.com/asset"
    })
    @DisplayName("ADV-04: Malicious, private, loopback, or credentialed URLs are rejected")
    void adversarialUrlsRejected(String dangerousUrl) throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Cloud Server",
                                "category", "DIGITAL_ACCOUNT",
                                "assetUrl", dangerousUrl
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }

    // =========================================================================
    // 5. Stage E Category Format Compatibility Checks
    // =========================================================================

    @Test
    @DisplayName("ADV-05: Stage E enforces category-specific format allowlist")
    void stageECategoryFormatEnforced() throws Exception {
        byte[] docxBytes = createMinimalDocx();

        // 1. DOCX uploaded as PROPERTY_DEED -> Rejected
        MockMultipartFile deedDocx = new MockMultipartFile("file", "deed.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(deedDocx)
                        .param("willId", willAId.toString())
                        .param("category", "PROPERTY_DEED")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_FORMAT"));

        // 2. DOCX uploaded as IDENTITY_RECORD -> Rejected
        MockMultipartFile idDocx = new MockMultipartFile("file", "id.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(idDocx)
                        .param("willId", willAId.toString())
                        .param("category", "IDENTITY_RECORD")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_FORMAT"));

        // 3. DOCX uploaded as FINANCIAL_RECORD -> Rejected
        MockMultipartFile finDocx = new MockMultipartFile("file", "statement.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(finDocx)
                        .param("willId", willAId.toString())
                        .param("category", "FINANCIAL_RECORD")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_FORMAT"));

        // 4. DOCX uploaded as WILL_ATTACHMENT -> Permitted
        MockMultipartFile attachDocx = new MockMultipartFile("file", "attachment.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(attachDocx)
                        .param("willId", willAId.toString())
                        .param("category", "WILL_ATTACHMENT")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("WILL_ATTACHMENT"));

        // 5. PNG uploaded as IDENTITY_RECORD -> Permitted
        MockMultipartFile idPng = new MockMultipartFile("file", "passport.png", "image/png", createMinimalPng());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(idPng)
                        .param("willId", willAId.toString())
                        .param("category", "IDENTITY_RECORD")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("IDENTITY_RECORD"));
    }

    // =========================================================================
    // 6. Numeric Overflow, Precision, and Year Bounds
    // =========================================================================

    @Test
    @DisplayName("ADV-06: Numeric magnitude, precision, and year bounds prevent database overflow")
    void numericAndPrecisionBoundsEnforced() throws Exception {
        // 1. Asset value exceeds 15 integer digits (NUMERIC(19,4) overflow defense)
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Overflow Value Asset",
                                "category", "INVESTMENT",
                                "assetValue", new BigDecimal("10000000000000000.00"), // 17 digits
                                "currency", "USD"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        // 2. Ownership percentage exceeds 2 decimal places (NUMERIC(5,2) scale defense)
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Excess Scale Ownership Asset",
                                "category", "REAL_ESTATE",
                                "ownershipPercentage", new BigDecimal("50.123")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        // 3. Asset year outside 1800-2100 DB constraint
        mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Medieval Personal Property",
                                "category", "PERSONAL_PROPERTY",
                                "assetYear", 1750
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE"));
    }

    // =========================================================================
    // 7. Non-Accepted Document Download Protection
    // =========================================================================

    @Test
    @DisplayName("ADV-07: Documents in non-ACCEPTED status cannot be downloaded")
    void nonAcceptedDocumentDownloadBlocked() {
        EncryptedDocument doc = documentRepository.findById(docAId).orElseThrow();
        doc.setStatus(DocumentStatus.NEEDS_REVIEW);
        documentRepository.save(doc);

        assertThatThrownBy(() -> documentService.downloadDocument(willAId, docAId, userAId.toString(), "OWNER"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not available for download");

        doc.setStatus(DocumentStatus.REJECTED);
        documentRepository.save(doc);

        assertThatThrownBy(() -> documentService.downloadDocument(willAId, docAId, userAId.toString(), "OWNER"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not available for download");
    }

    // =========================================================================
    // 8. Internal Job Secret Authorization
    // =========================================================================

    @Test
    @DisplayName("ADV-08: Internal scheduled job endpoints require valid secret header")
    void internalJobSecretAuthorizationEnforced() throws Exception {
        // Missing secret header -> 401 Unauthorized
        mockMvc.perform(post("/internal/jobs/process-inactivity"))
                .andExpect(status().isUnauthorized());

        // Incorrect secret header -> 401 Unauthorized
        mockMvc.perform(post("/internal/jobs/process-inactivity")
                        .header("X-Internal-Job-Secret", "wrong-secret-token"))
                .andExpect(status().isUnauthorized());

        // Correct secret header -> 200 OK
        mockMvc.perform(post("/internal/jobs/process-inactivity")
                        .header("X-Internal-Job-Secret", "digitalwill-internal-secret-token"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 9. Unknown Category Error Handling & Reflection Leak Prevention
    // =========================================================================

    @Test
    @DisplayName("ADV-09: Unknown category produces stable INVALID_CATEGORY code without leaking internal class types")
    void unknownCategoryReturnsStableCodeWithoutReflectionLeak() throws Exception {
        String response = mockMvc.perform(post("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Insurance Holding\",\"category\":\"LIFE_INSURANCE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"))
                .andReturn().getResponse().getContentAsString();

        // Must NOT leak internal Java class name
        assertThat(response).doesNotContain("com.digitalwill.estate.model.AssetCategory");
        assertThat(response).doesNotContain("InvalidFormatException");
    }
}
