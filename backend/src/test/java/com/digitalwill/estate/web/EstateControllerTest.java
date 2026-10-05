package com.digitalwill.estate.web;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Instant baseTime = Instant.parse("2026-07-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
    }

    @Test
    void createWill_andQuery_success() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
                "ownerId", ownerId.toString(),
                "title", "Primary Estate Plan"
        ));

        String response = mockMvc.perform(post("/api/wills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Primary Estate Plan"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(response, Map.class);
        String willId = (String) map.get("id");

        mockMvc.perform(get("/api/wills/" + willId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(willId))
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    @Test
    void checkIn_updatesActivity() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Check-in Will"
        ));
        String response = mockMvc.perform(post("/api/wills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(response, Map.class);
        String willId = (String) map.get("id");

        mockMvc.perform(post("/api/wills/" + willId + "/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    @Test
    void addAsset_andBeneficiary_andAllocate() throws Exception {
        String willBody = objectMapper.writeValueAsString(Map.of("title", "Assets Will"));
        String willRes = mockMvc.perform(post("/api/wills")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharePercentage").value(100));

        // Audit verify
        mockMvc.perform(get("/api/wills/" + willId + "/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }
}
