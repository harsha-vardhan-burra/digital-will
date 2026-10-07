package com.digitalwill.estate.web;

import com.digitalwill.auth.service.AuthService;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.model.AssetCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
class EstateControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestTimeProvider timeProvider;
    @Autowired AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Instant baseTime = Instant.parse("2026-07-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
    }

    private String getAuthToken(String prefix) {
        String email = prefix + "_" + UUID.randomUUID() + "@example.com";
        return authService.register(email, "Password123!", "Test User").token();
    }

    @Test
    void createWill_andQuery_success() throws Exception {
        String token = getAuthToken("create");
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Primary Estate Plan"
        ));

        String response = mockMvc.perform(post("/api/wills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Primary Estate Plan"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(response, Map.class);
        String willId = (String) map.get("id");

        mockMvc.perform(get("/api/wills/" + willId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(willId))
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    @Test
    void checkIn_updatesActivity() throws Exception {
        String token = getAuthToken("checkin");
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Check-in Will"
        ));
        String response = mockMvc.perform(post("/api/wills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(response, Map.class);
        String willId = (String) map.get("id");

        mockMvc.perform(post("/api/wills/" + willId + "/check-in")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    @Test
    void addAsset_andBeneficiary_andAllocate() throws Exception {
        String token = getAuthToken("estate");
        String willBody = objectMapper.writeValueAsString(Map.of("title", "Assets Will"));
        String willRes = mockMvc.perform(post("/api/wills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(willBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String willId = (String) objectMapper.readValue(willRes, Map.class).get("id");

        // Add asset
        String assetBody = objectMapper.writeValueAsString(Map.of(
                "title", "Family Home",
                "category", "REAL_ESTATE",
                "description", "Primary Residence",
                "encryptedAccessData", "secret-enc-ref",
                "instructions", "Leave to daughter"
        ));
        String assetRes = mockMvc.perform(post("/api/wills/" + willId + "/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assetBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Family Home"))
                .andReturn().getResponse().getContentAsString();
        String assetId = (String) objectMapper.readValue(assetRes, Map.class).get("id");

        // Add beneficiary
        String beneBody = objectMapper.writeValueAsString(Map.of(
                "name", "Jane Doe",
                "email", "jane@example.com",
                "relationship", "Daughter"
        ));
        String beneRes = mockMvc.perform(post("/api/wills/" + willId + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(beneBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andReturn().getResponse().getContentAsString();
        String beneId = (String) objectMapper.readValue(beneRes, Map.class).get("id");

        // Allocate
        String allocBody = objectMapper.writeValueAsString(Map.of(
                "assetId", assetId,
                "beneficiaryId", beneId,
                "sharePercentage", 100,
                "instructions", "Direct transfer"
        ));
        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharePercentage").value(100));

        // Audit verify
        mockMvc.perform(get("/api/wills/" + willId + "/audit/verify")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void allocationInvariants_rejectPartialAndExceeding_accept100Percent() throws Exception {
        String token = getAuthToken("alloc_invariants");
        String willRes = mockMvc.perform(post("/api/wills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Allocation Invariant Will"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String willId = (String) objectMapper.readValue(willRes, Map.class).get("id");

        // Add 1 asset
        String assetRes = mockMvc.perform(post("/api/wills/" + willId + "/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Test Real Estate",
                                "category", "REAL_ESTATE",
                                "description", "Estate property",
                                "instructions", "To be shared"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String assetId = (String) objectMapper.readValue(assetRes, Map.class).get("id");

        // Add 2 beneficiaries
        String b1Res = mockMvc.perform(post("/api/wills/" + willId + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Beneficiary One",
                                "email", "b1_" + UUID.randomUUID() + "@example.com",
                                "relationship", "Friend"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String b1Id = (String) objectMapper.readValue(b1Res, Map.class).get("id");

        String b2Res = mockMvc.perform(post("/api/wills/" + willId + "/beneficiaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Beneficiary Two",
                                "email", "b2_" + UUID.randomUUID() + "@example.com",
                                "relationship", "Colleague"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String b2Id = (String) objectMapper.readValue(b2Res, Map.class).get("id");

        // Add 3 trusted contacts
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/wills/" + willId + "/contacts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Contact " + i,
                                    "email", "contact" + i + "_" + UUID.randomUUID() + "@example.com"
                            ))))
                    .andExpect(status().isCreated());
        }

        // Test Case 1: 50% + 30% = 80% -> rejected from ready status (allAssetsFullyAllocated = false, ready = false)
        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", assetId,
                                "beneficiaryId", b1Id,
                                "sharePercentage", 50
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", assetId,
                                "beneficiaryId", b2Id,
                                "sharePercentage", 30
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wills/" + willId + "/review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allAssetsFullyAllocated").value(false))
                .andExpect(jsonPath("$.readyForActivation").value(false))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("80% allocated")));

        // Test Case 2: 50% + 40% = 90% (or 60% + 30% = 90%) -> rejected from ready status
        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", assetId,
                                "beneficiaryId", b2Id,
                                "sharePercentage", 40
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wills/" + willId + "/review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allAssetsFullyAllocated").value(false))
                .andExpect(jsonPath("$.readyForActivation").value(false))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("90% allocated")));

        // Test Case 3: Over-allocation (50% existing + 60% requested = 110%) -> rejected with 400 Bad Request
        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", assetId,
                                "beneficiaryId", b2Id,
                                "sharePercentage", 60
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        // Test Case 4: 50% + 50% = 100% -> accepted and ready
        mockMvc.perform(post("/api/wills/" + willId + "/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assetId", assetId,
                                "beneficiaryId", b2Id,
                                "sharePercentage", 50
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wills/" + willId + "/review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allAssetsFullyAllocated").value(true))
                .andExpect(jsonPath("$.readyForActivation").value(true))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }
}
