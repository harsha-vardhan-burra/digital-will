package com.digitalwill.security;

import com.digitalwill.auth.service.AuthService;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.document.service.DocumentService;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
public class AuthorizationIdorSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired WillStateService willStateService;
    @Autowired EstateService estateService;
    @Autowired DocumentService documentService;
    @Autowired VerificationService verificationService;
    @Autowired TestTimeProvider timeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");

    private String userAToken;
    private UUID userAId;
    private UUID willAId;

    private String userBToken;
    private UUID userBId;
    private UUID willBId;

    private UUID assetAId;
    private UUID beneficiaryAId;
    private UUID docAId;

    @BeforeEach
    void setUp() throws Exception {
        timeProvider.setNow(baseTime);

        // User A setup
        String emailA = "usera_" + UUID.randomUUID() + "@example.com";
        AuthService.AuthResponse authA = authService.register(emailA, "PasswordA123!", "User Alpha");
        userAToken = authA.token();
        userAId = authA.userId();

        WillStateEntity willA = willStateService.createWill(userAId, "Alpha Estate Plan");
        willAId = willA.getId();

        Asset assetA = estateService.addAsset(willAId, "Alpha Vault", AssetCategory.CRYPTO, "Secret keys", "enc-data", "inst");
        assetAId = assetA.getId();

        Beneficiary benA = estateService.addBeneficiary(willAId, "Alice Beneficiary", "alice@example.com", "Child");
        beneficiaryAId = benA.getId();

        estateService.allocateAsset(willAId, assetAId, beneficiaryAId, 100, "Full transfer");

        verificationService.addTrustedContactToWill(willAId, "Contact Alpha", "contact.alpha@example.com");

        var docA = documentService.uploadDocument(willAId, userAId, "alpha_vault.pdf", "application/pdf", "Confidential Alpha".getBytes(StandardCharsets.UTF_8));
        docAId = docA.getId();

        // User B setup
        String emailB = "userb_" + UUID.randomUUID() + "@example.com";
        AuthService.AuthResponse authB = authService.register(emailB, "PasswordB123!", "User Beta");
        userBToken = authB.token();
        userBId = authB.userId();

        WillStateEntity willB = willStateService.createWill(userBId, "Beta Estate Plan");
        willBId = willB.getId();
    }

    @Test
    @DisplayName("ATK-05: User B attempting to GET User A's will is forbidden (403)")
    void userBCannotGetWillA() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-05: User B attempting check-in on User A's will is forbidden (403)")
    void userBCannotCheckInWillA() throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/check-in")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-05: User B attempting cancel on User A's will is forbidden (403)")
    void userBCannotCancelWillA() throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/cancel")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-05: User B attempting schedule-release on User A's will is forbidden (403)")
    void userBCannotScheduleReleaseWillA() throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/schedule-release")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-05: User B attempting execute-release on User A's will is forbidden (403)")
    void userBCannotExecuteReleaseWillA() throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/execute-release")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-06: User B attempting to delete User A's asset is forbidden (403)")
    void userBCannotDeleteAssetA() throws Exception {
        mockMvc.perform(delete("/api/wills/" + willAId + "/assets/" + assetAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-06: User B attempting to delete User A's beneficiary is forbidden (403)")
    void userBCannotDeleteBeneficiaryA() throws Exception {
        mockMvc.perform(delete("/api/wills/" + willAId + "/beneficiaries/" + beneficiaryAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-07: Cross-will asset allocation injection fails (Asset belongs to Will A, target is Will B)")
    void crossWillAssetAllocation_isRejected() throws Exception {
        // User B tries to allocate User A's asset to User B's beneficiary in Will B
        Beneficiary benB = estateService.addBeneficiary(willBId, "Bob Beneficiary", "bob@example.com", "Friend");

        String body = objectMapper.writeValueAsString(Map.of(
                "assetId", assetAId,
                "beneficiaryId", benB.getId(),
                "sharePercentage", 100
        ));

        mockMvc.perform(post("/api/wills/" + willBId + "/allocations")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    @DisplayName("ATK-07: Cross-will beneficiary allocation injection fails (Beneficiary belongs to Will A, target is Will B)")
    void crossWillBeneficiaryAllocation_isRejected() throws Exception {
        Asset assetB = estateService.addAsset(willBId, "Beta Asset", AssetCategory.REAL_ESTATE, "House", null, null);

        String body = objectMapper.writeValueAsString(Map.of(
                "assetId", assetB.getId(),
                "beneficiaryId", beneficiaryAId, // Beneficiary of Will A
                "sharePercentage", 100
        ));

        mockMvc.perform(post("/api/wills/" + willBId + "/allocations")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    @DisplayName("ATK-06: User B cannot download or view metadata of User A's document (403)")
    void userBCannotAccessUserADocuments() throws Exception {
        // Metadata
        mockMvc.perform(get("/api/documents/" + docAId + "/metadata")
                        .param("willId", willAId.toString())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Download
        mockMvc.perform(get("/api/documents/" + docAId + "/download")
                        .param("willId", willAId.toString())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-06: User B cannot upload documents into User A's will (403)")
    void userBCannotUploadDocToWillA() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "exploit.txt", "text/plain", "malicious payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willAId.toString())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-05: User B cannot access User A's audit trail or verify its chain (403)")
    void userBCannotAccessUserAAudit() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId + "/audit")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/wills/" + willAId + "/audit/verify")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ATK-05: User B cannot access User A's readiness review (403)")
    void userBCannotAccessUserAReview() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId + "/review")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Autowired com.digitalwill.estate.web.EstateController estateController;
    @Autowired com.digitalwill.document.web.DocumentController documentController;

    @Test
    @DisplayName("ATK-08: EstateController must fail closed when principal is null")
    void estateController_nullPrincipal_failsClosed() {
        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class, () -> {
            estateController.getWill(willAId, null);
        });
    }

    @Test
    @DisplayName("ATK-08: DocumentController must fail closed when principal is null")
    void documentController_nullPrincipal_failsClosed() {
        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class, () -> {
            documentController.getMetadata(docAId, willAId, null);
        });
    }
}
