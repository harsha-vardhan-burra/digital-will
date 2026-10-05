package com.digitalwill.document.web;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired WillStateService willStateService;
    @Autowired TestTimeProvider timeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID willId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        timeProvider.setNow(Instant.parse("2026-08-01T12:00:00Z"));
        ownerId = UUID.randomUUID();
        WillStateEntity will = willStateService.createWill(ownerId, "Doc Test Will");
        willId = will.getId();
    }

    @Test
    void upload_andDownload_success() throws Exception {
        byte[] fileBytes = "Sensitive Confidential Will Annexure".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "annexure.txt",
                "text/plain",
                fileBytes
        );

        String uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willId.toString())
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("annexure.txt"))
                .andExpect(jsonPath("$.fileSize").value(fileBytes.length))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(uploadRes, Map.class);
        String docId = (String) map.get("id");

        // Download
        byte[] downloaded = mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .param("willId", willId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(fileBytes);
    }

    @Test
    void download_documentNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID() + "/download")
                        .param("willId", willId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }
}
