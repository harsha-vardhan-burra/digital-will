package com.digitalwill.estate.web;

import com.digitalwill.auth.service.AuthService;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
class WillOwnershipIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TestTimeProvider timeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String userAToken;
    private String userBToken;
    private String willAId;

    @BeforeEach
    void setUp() throws Exception {
        timeProvider.setNow(Instant.parse("2026-10-01T12:00:00Z"));

        // Register User A and create Will A
        String emailA = "usera_" + UUID.randomUUID() + "@example.com";
        userAToken = authService.register(emailA, "Password123!", "User Alpha").token();

        String willRes = mockMvc.perform(post("/api/wills")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Alpha Will"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        willAId = (String) objectMapper.readValue(willRes, Map.class).get("id");

        // Register User B
        String emailB = "userb_" + UUID.randomUUID() + "@example.com";
        userBToken = authService.register(emailB, "Password123!", "User Beta").token();
    }

    @Test
    @DisplayName("Workflow 2: User B cannot access User A's Will -> 403 Forbidden")
    void userBCannotAccessUserAWill() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot view User A's assets -> 403 Forbidden")
    void userBCannotAccessUserAAssets() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId + "/assets")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot view User A's beneficiaries -> 403 Forbidden")
    void userBCannotAccessUserABeneficiaries() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId + "/beneficiaries")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot trigger check-in on User A's Will -> 403 Forbidden")
    void userBCannotTriggerCheckInOnUserAWill() throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/check-in")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot upload documents to User A's Will -> 403 Forbidden")
    void userBCannotUploadToUserAWill() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "exploit.pdf", "application/pdf", "malicious payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot list documents from User A's Will -> 403 Forbidden")
    void userBCannotListDocumentsFromUserAWill() throws Exception {
        mockMvc.perform(get("/api/documents/will/" + willAId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User cannot create a second Will (1 Will per user MVP limit) -> 409 Conflict")
    void userCannotCreateSecondWill() throws Exception {
        mockMvc.perform(post("/api/wills")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Duplicate Will"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot view User A's allocations -> 403 Forbidden")
    void userBCannotAccessUserAAllocations() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId + "/allocations")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot create allocation on User A's Will -> 403 Forbidden")
    void userBCannotAllocateUserAWill() throws Exception {
        mockMvc.perform(post("/api/wills/" + willAId + "/allocations")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", UUID.randomUUID(),
                                "beneficiaryId", UUID.randomUUID(),
                                "sharePercentage", 100
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot view User A's contacts -> 403 Forbidden")
    void userBCannotAccessUserAContacts() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId + "/contacts")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 2: User B cannot view User A's review checklist -> 403 Forbidden")
    void userBCannotAccessUserAReview() throws Exception {
        mockMvc.perform(get("/api/wills/" + willAId + "/review")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
