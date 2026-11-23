package edu.group17.roommates.account;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import edu.group17.roommates.support.TestIdentityConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@SpringBootTest(properties = {"app.firestore.enabled=true", "app.firestore.project-id=demo-roommate-expenses"})
@AutoConfigureMockMvc
@Import(TestIdentityConfiguration.class)
class UserProfileSyncIntegrationTest {
    @Autowired Firestore firestore;
    @Autowired MockMvc mvc;

    private final String uid = "profile-sync-" + UUID.randomUUID();
    private final Map<String, Object> existingProfile = Map.of("uid", uid, "username", "previous", "theme", "dark");
    private DocumentReference profile;

    @BeforeEach
    void seedExistingProfile() throws Exception {
        profile = firestore.collection("userProfiles").document(UserProfileStore.documentId(uid));
        profile.set(existingProfile).get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void removeProfile() throws Exception {
        if (profile != null) {
            profile.delete().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void blankDisplayNameUsesTheEmailUsernameAndPreservesOtherFields() throws Exception {
        String identity = token(uid, Map.of("name", "  ", "email", "avery@example.test"), false);

        mvc.perform(get("/api/me").header(AUTHORIZATION, "Bearer " + identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(uid));

        var updated = profile.get().get(10, TimeUnit.SECONDS);
        assertEquals(Map.of("uid", uid, "username", "avery", "theme", "dark"), updated.getData());
    }

    @Test
    void unusableClaimsLeaveAnExistingProfileIntact() throws Exception {
        String identity = token(uid, Map.of("name", "  ", "email", "@example.test"), false);

        mvc.perform(get("/api/me").header(AUTHORIZATION, "Bearer " + identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(uid));

        var unchanged = profile.get().get(10, TimeUnit.SECONDS);
        assertEquals(existingProfile, unchanged.getData());
    }
}
