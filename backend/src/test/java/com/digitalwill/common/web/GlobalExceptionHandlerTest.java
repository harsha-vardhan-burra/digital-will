package com.digitalwill.common.web;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.document.exception.DocumentNotFoundException;
import com.digitalwill.release.exception.DisclosureNotReadyException;
import com.digitalwill.release.exception.DisclosureTokenExpiredException;
import com.digitalwill.release.exception.DisclosureTokenRevokedException;
import com.digitalwill.state.exception.IllegalStateTransitionException;
import com.digitalwill.state.model.WillEvent;
import com.digitalwill.state.model.WillState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestTimeConfig.class, GlobalExceptionHandlerTest.DummyErrorController.class})
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestTimeProvider timeProvider;

    @RestController
    @RequestMapping("/test/errors")
    static class DummyErrorController {
        @GetMapping("/transition")
        public void throwTransition() {
            throw new IllegalStateTransitionException(WillState.ACTIVE, WillEvent.DISCLOSURE_COMPLETED);
        }

        @GetMapping("/not-found")
        public void throwNotFound() {
            throw new DocumentNotFoundException("Test document not found");
        }

        @GetMapping("/disclosure-expired")
        public void throwExpired() {
            throw new DisclosureTokenExpiredException("Token expired");
        }

        @GetMapping("/disclosure-consumed")
        public void throwConsumed() {
            throw new com.digitalwill.release.exception.DisclosureTokenConsumedException("Token already consumed");
        }

        @GetMapping("/disclosure-revoked")
        public void throwRevoked() {
            throw new DisclosureTokenRevokedException("Token revoked");
        }

        @GetMapping("/not-ready")
        public void throwNotReady() {
            throw new DisclosureNotReadyException("Disclosure not ready");
        }

        @GetMapping("/security")
        public void throwSecurity() {
            throw new SecurityException("Unauthorized");
        }

        @GetMapping("/internal")
        public void throwInternal() {
            throw new RuntimeException("Unexpected runtime error");
        }
    }

    @BeforeEach
    void setUp() {
        timeProvider.setNow(Instant.parse("2026-06-01T12:00:00Z"));
    }

    @Test
    void illegalStateTransition_returns409Conflict_withStandardFormat() throws Exception {
        mockMvc.perform(get("/test/errors/transition").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.timestamp").value("2026-06-01T12:00:00Z"))
                .andExpect(jsonPath("$.path").value("/test/errors/transition"));
    }

    @Test
    void documentNotFound_returns404NotFound() throws Exception {
        mockMvc.perform(get("/test/errors/not-found").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Test document not found"));
    }

    @Test
    void disclosureExpired_returns410Gone() throws Exception {
        mockMvc.perform(get("/test/errors/disclosure-expired").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.code").value("DISCLOSURE_TOKEN_EXPIRED"));
    }

    @Test
    void disclosureConsumed_returns409Conflict() throws Exception {
        mockMvc.perform(get("/test/errors/disclosure-consumed").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DISCLOSURE_TOKEN_ALREADY_CONSUMED"));
    }

    @Test
    void disclosureRevoked_returns410Gone() throws Exception {
        mockMvc.perform(get("/test/errors/disclosure-revoked").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.code").value("DISCLOSURE_TOKEN_REVOKED"));
    }

    @Test
    void disclosureNotReady_returns409Conflict() throws Exception {
        mockMvc.perform(get("/test/errors/not-ready").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DISCLOSURE_NOT_READY"));
    }

    @Test
    void securityException_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/test/errors/security").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalError_returns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/errors/internal").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred"));
    }
}
