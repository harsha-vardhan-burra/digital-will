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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
class EstateWorkflowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TestTimeProvider timeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
    }

    @Test
    @DisplayName("Workflow 1: End-to-end authenticated Will setup journey")
    void completeWillSetupJourney() throws Exception {
        // 1. Authenticate user
        String email = "owner_" + UUID.randomUUID() + "@example.com";
        String token = authService.register(email, "StrongPass123!", "Estate Testator").token();

        // 2. Create Will
        String willRes = mockMvc.perform(post("/api/wills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Master Succession Will"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Master Succession Will"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String willId = (String) objectMapper.readValue(willRes, Map.class).get("id");

        // 3. Add Asset 1 (Real Estate)
        String asset1Res = mockMvc.perform(post("/api/wills/" + willId + "/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Downtown Apartment",
                                "category", "REAL_ESTATE",
                                "description", "Unit 404, City Tower",
                                "instructions", "Transfer to heir"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Downtown Apartment"))
                .andReturn().getResponse().getContentAsString();
        String asset1Id = (String) objectMapper.readValue(asset1Res, Map.class).get("id");

        // 4. Add Asset 2 (Digital Account)
        String asset2Res = mockMvc.perform(post("/api/wills/" + willId + "/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "GitHub & Cloudflare Access",
                                "category", "DIGITAL_ACCOUNT",
                                "description", "Digital infrastructure management",
                                "instructions", "Grant access to tech executor"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String asset2Id = (String) objectMapper.readValue(asset2Res, Map.class).get("id");

        // 5. Add Beneficiaries
        String b1Res = mockMvc.perform(post("/api/wills/" + willId + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sophia Miller",
                                "email", "sophia@example.com",
                                "relationship", "Daughter"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String b1Id = (String) objectMapper.readValue(b1Res, Map.class).get("id");

        String b2Res = mockMvc.perform(post("/api/wills/" + willId + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Lucas Miller",
                                "email", "lucas@example.com",
                                "relationship", "Son"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String b2Id = (String) objectMapper.readValue(b2Res, Map.class).get("id");

        // 6. Define Allocations (100% distribution per asset)
        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", asset1Id,
                                "beneficiaryId", b1Id,
                                "sharePercentage", 100,
                                "instructions", "Direct allocation"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharePercentage").value(100));

        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", asset2Id,
                                "beneficiaryId", b2Id,
                                "sharePercentage", 100,
                                "instructions", "Tech estate management"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharePercentage").value(100));

        // 7. Configure 3 Trusted Contacts (satisfying 2-of-3 quorum requirement)
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/wills/" + willId + "/contacts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Trusted Contact " + i,
                                    "email", "contact" + i + "_" + UUID.randomUUID() + "@example.com"
                            ))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        // 8. Attach Envelope-Encrypted Supporting Document
        MockMultipartFile deedFile = new MockMultipartFile(
                "file", "property_deed.pdf", "application/pdf",
                "Certified Title Deed Content".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(deedFile)
                        .param("willId", willId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("property_deed.pdf"));

        // 9. Review Will (Verify completeness checklist)
        mockMvc.perform(get("/api/wills/" + willId + "/review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCount").value(2))
                .andExpect(jsonPath("$.beneficiaryCount").value(2))
                .andExpect(jsonPath("$.allocationCount").value(2))
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.activeTrustedContactCount").value(3))
                .andExpect(jsonPath("$.hasAssets").value(true))
                .andExpect(jsonPath("$.hasBeneficiaries").value(true))
                .andExpect(jsonPath("$.allAssetsFullyAllocated").value(true))
                .andExpect(jsonPath("$.hasQuorumContacts").value(true))
                .andExpect(jsonPath("$.readyForActivation").value(true));

        // 10. Check-in (Updates activity timestamp)
        mockMvc.perform(post("/api/wills/" + willId + "/check-in")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        // 11. Verify Audit Chain
        mockMvc.perform(get("/api/wills/" + willId + "/audit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
