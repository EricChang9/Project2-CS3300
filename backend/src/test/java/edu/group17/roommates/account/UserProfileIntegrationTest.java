package edu.group17.roommates.account;

import com.google.cloud.firestore.Firestore;
import edu.group17.roommates.household.HouseholdService;
import edu.group17.roommates.invitation.InvitationService;
import edu.group17.roommates.support.TestIdentityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@SpringBootTest(properties = {"app.firestore.enabled=true", "app.firestore.project-id=demo-roommate-expenses"})
@AutoConfigureMockMvc
@Import(TestIdentityConfiguration.class)
class UserProfileIntegrationTest {
    @Autowired Firestore firestore;
    @Autowired HouseholdService households;
    @Autowired InvitationService invitations;
    @Autowired MockMvc mvc;

    @Test void verifiedNamesFollowMembersAndUpdateWithoutRejoining() throws Exception {
        String owner = "profile-owner-"+UUID.randomUUID(), guest = "profile-guest-"+UUID.randomUUID();
        String householdId = null;
        try {
            mvc.perform(get("/api/me?displayName=forged").header("Authorization", "Bearer "+token(owner,
                    Map.of("name", "Joseph"), false))).andExpect(status().isOk());
            Map<String,Object> claims = new HashMap<>(); claims.put("name", null); claims.put("email", "roomie@example.test");
            mvc.perform(get("/api/me").header("Authorization", "Bearer "+token(guest, claims, false)))
                    .andExpect(status().isOk());
            var household = households.create("Profile test", owner); householdId = household.id();
            assertEquals("Joseph", household.members().getFirst().username());
            invitations.redeem(invitations.create(householdId, owner).code(), guest);
            var members = households.list(owner).getFirst().members();
            assertEquals("Joseph", members.stream().filter(m -> m.uid().equals(owner)).findFirst().orElseThrow().username());
            assertEquals("roomie", members.stream().filter(m -> m.uid().equals(guest)).findFirst().orElseThrow().username());
            mvc.perform(get("/api/me").header("Authorization", "Bearer "+token(guest, Map.of("name", "Sam"), false)))
                    .andExpect(status().isOk());
            assertEquals("Sam", households.list(owner).getFirst().members().stream()
                    .filter(m -> m.uid().equals(guest)).findFirst().orElseThrow().username());
        } finally {
            if (householdId != null) {
                var ref = firestore.collection("households").document(householdId);
                for (var member : ref.collection("members").get().get(10, TimeUnit.SECONDS).getDocuments())
                    member.getReference().delete().get(10, TimeUnit.SECONDS);
                for (var invite : firestore.collection("householdInvitations").whereEqualTo("householdId", householdId)
                        .get().get(10, TimeUnit.SECONDS).getDocuments()) invite.getReference().delete().get(10, TimeUnit.SECONDS);
                ref.delete().get(10, TimeUnit.SECONDS);
            }
            for (String uid : List.of(owner, guest)) firestore.collection("userProfiles").document(UserProfileStore.documentId(uid))
                    .delete().get(10, TimeUnit.SECONDS);
        }
    }
}
