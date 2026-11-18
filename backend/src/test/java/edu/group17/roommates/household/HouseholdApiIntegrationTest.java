package edu.group17.roommates.household;

import com.google.cloud.firestore.Firestore;
import com.jayway.jsonpath.JsonPath;
import edu.group17.roommates.support.TestIdentityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@SpringBootTest(properties = {"app.firestore.enabled=true", "app.firestore.project-id=demo-roommate-expenses"})
@AutoConfigureMockMvc
@Import(TestIdentityConfiguration.class)
class HouseholdApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired Firestore firestore;

    @Test
    void authenticatedCreateAndListUseRealPersistenceAndIsolateUsers() throws Exception {
        String alice = "alice-" + UUID.randomUUID();
        String bob = "bob-" + UUID.randomUUID();
        String id = null;
        try {
            var result = mvc.perform(post("/api/households").contentType("application/json")
                    .content("{\"name\":\"  Apartment  \"}")
                    .header("Authorization", "Bearer " + token(alice)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Apartment"))
                    .andExpect(jsonPath("$.createdBy").value(alice))
                    .andExpect(jsonPath("$.members[0].uid").value(alice)).andReturn();
            id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
            mvc.perform(get("/api/households").header("Authorization", "Bearer " + token(alice)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(id));
            mvc.perform(get("/api/households?uid=" + alice).header("Authorization", "Bearer " + token(bob)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        } finally {
            if (id != null) {
                var document = firestore.collection("households").document(id);
                for (var member : document.collection("members").get().get(10, TimeUnit.SECONDS).getDocuments()) {
                    member.getReference().delete().get(10, TimeUnit.SECONDS);
                }
                document.delete().get(10, TimeUnit.SECONDS);
            }
        }
    }
}
