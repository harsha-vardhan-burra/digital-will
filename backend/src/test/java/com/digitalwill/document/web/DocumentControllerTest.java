package com.digitalwill.document.web;

import com.digitalwill.auth.service.AuthService;
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
    @Autowired AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID willId;
    private UUID ownerId;
    private String authToken;

    @BeforeEach
    void setUp() {
        timeProvider.setNow(Instant.parse("2026-08-01T12:00:00Z"));
        String email = "docowner_" + UUID.randomUUID() + "@example.com";
        AuthService.AuthResponse reg = authService.register(email, "Password123!", "Document Owner");
        ownerId = reg.userId();
        authToken = reg.token();

        WillStateEntity will = willStateService.createWill(ownerId, "Doc Test Will");
        willId = will.getId();
    }

    private byte[] createMinimalPdf() {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void upload_andDownload_success() throws Exception {
        byte[] fileBytes = createMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "annexure.pdf",
                "application/pdf",
                fileBytes
        );

        String uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willId.toString())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("annexure.pdf"))
                .andExpect(jsonPath("$.fileSize").value(fileBytes.length))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(uploadRes, Map.class);
        String docId = (String) map.get("id");

        // Download
        byte[] downloaded = mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .param("willId", willId.toString())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(fileBytes);
    }

    @Test
    void upload_withQuotesAndSpecialCharactersInFileName_succeeds() throws Exception {
        byte[] fileBytes = createMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Alice's \"Private\" Will & Annexure.pdf",
                "application/pdf",
                fileBytes
        );

        String uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willId.toString())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Alice's \"Private\" Will & Annexure.pdf"))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(uploadRes, Map.class);
        String docId = (String) map.get("id");

        byte[] downloaded = mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .param("willId", willId.toString())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(fileBytes);
    }

    @Test
    void download_documentNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID() + "/download")
                        .param("willId", willId.toString())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(emptyFile)
                        .param("willId", willId.toString())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void upload_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.txt",
                "text/plain",
                "sample".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willId.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_unauthorizedActor_returns403() throws Exception {
        String otherToken = authService.register("other_" + UUID.randomUUID() + "@example.com", "Password123!", "Other User").token();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.txt",
                "text/plain",
                "sample".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("willId", willId.toString())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
