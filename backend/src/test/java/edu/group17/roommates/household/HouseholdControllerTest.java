package edu.group17.roommates.household;

import edu.group17.roommates.config.FirebaseJwtConfiguration;
import edu.group17.roommates.config.SecurityConfig;
import edu.group17.roommates.support.TestIdentityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HouseholdController.class, properties = "app.firestore.enabled=true")
@Import({SecurityConfig.class, FirebaseJwtConfiguration.class, TestIdentityConfiguration.class})
class HouseholdControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HouseholdService service;

    private Household household() {
        return new Household("generated-id", "Home", "alice", Instant.parse("2026-11-01T12:00:00Z"),
                List.of(new Household.Member("alice", Instant.parse("2026-11-01T12:00:00Z"))));
    }

    @Test
    void createsWithVerifiedCreatorAndReturnsServerFields() throws Exception {
        when(service.create("Home", "alice")).thenReturn(household());
        mvc.perform(post("/api/households").contentType("application/json")
                .content("{\"name\":\"Home\",\"createdBy\":\"mallory\",\"id\":\"forged-id\"}")
                .header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("generated-id"))
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-01T12:00:00Z"))
                .andExpect(jsonPath("$.members[0].uid").value("alice"));
        verify(service).create("Home", "alice");
    }

    @Test
    void listingCannotSelectAnotherUsersIdentity() throws Exception {
        when(service.list("alice")).thenReturn(List.of(household()));
        mvc.perform(get("/api/households?uid=mallory").header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("generated-id"));
        verify(service).list("alice");
        verify(service, never()).list("mallory");
    }

    @Test
    void rejectsInvalidNamesAndUnauthenticatedWritesBeforeCallingService() throws Exception {
        for (String body : new String[]{"{}", "{\"name\":\" \"}", "{\"name\":\"" + "x".repeat(81) + "\"}"}) {
            mvc.perform(post("/api/households").contentType("application/json").content(body)
                    .header("Authorization", "Bearer " + token("alice"))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/households").contentType("application/json").content("{\"name\":\"Home\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/households")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void storageFailureReturnsGenericServiceUnavailable() throws Exception {
        when(service.create("Home", "alice")).thenThrow(
                new HouseholdStorageException(new RuntimeException("private database detail")));
        mvc.perform(post("/api/households").contentType("application/json").content("{\"name\":\"Home\"}")
                .header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Household storage is unavailable"));
    }
}
