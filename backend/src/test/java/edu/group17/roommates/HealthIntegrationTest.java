package edu.group17.roommates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HealthIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void healthIsPublicAndReturnsServiceStatus() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("roommate-expenses"));
    }

    @Test
    void householdEndpointsAreClosedUntilAuthenticationExists() throws Exception {
        mvc.perform(get("/api/households")).andExpect(status().isForbidden());
    }

    @Test
    void writesRequireCsrfProtection() throws Exception {
        mvc.perform(post("/api/health")).andExpect(status().isForbidden());
    }
}
