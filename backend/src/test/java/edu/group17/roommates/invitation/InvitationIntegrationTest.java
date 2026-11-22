package edu.group17.roommates.invitation;

import com.google.cloud.firestore.*;
import com.jayway.jsonpath.JsonPath;
import edu.group17.roommates.household.*;
import edu.group17.roommates.support.TestIdentityConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@SpringBootTest(properties = {"app.firestore.enabled=true", "app.firestore.project-id=demo-roommate-expenses"})
@AutoConfigureMockMvc
@Import(TestIdentityConfiguration.class)
class InvitationIntegrationTest {
    @Autowired Firestore firestore;
    @Autowired HouseholdService households;
    @Autowired HouseholdAccessGuard access;
    @Autowired InvitationService invitations;
    @Autowired MockMvc mvc;
    private final String owner = "owner/" + UUID.randomUUID();
    private final String roommate = "roommate/" + UUID.randomUUID();
    private Household home;

    @BeforeEach void createHome() { home = households.create("Integration home", owner); }

    @AfterEach void cleanOnlyThisTestsDocuments() throws Exception {
        if (home == null) return;
        var reference = firestore.collection("households").document(home.id());
        for (var document : reference.collection("members").get().get(10, TimeUnit.SECONDS).getDocuments()) {
            document.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        for (var document : firestore.collection("householdInvitations").whereEqualTo("householdId", home.id())
                .get().get(10, TimeUnit.SECONDS).getDocuments()) {
            document.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        reference.delete().get(10, TimeUnit.SECONDS);
    }

    @Test void createsProtectedCodesAndJoinsThroughAuthenticatedHttp() throws Exception {
        mvc.perform(post("/api/households/"+home.id()+"/invitations")
                .header("Authorization", "Bearer "+token(roommate))).andExpect(status().isNotFound());
        var result = mvc.perform(post("/api/households/"+home.id()+"/invitations")
                .header("Authorization", "Bearer "+token(owner))).andExpect(status().isCreated()).andReturn();
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
        assertTrue(code.matches("[A-Za-z0-9_-]{43}"));
        var stored = firestore.collection("householdInvitations").document(InvitationService.codeHash(code))
                .get().get(10, TimeUnit.SECONDS);
        assertEquals(home.id(), stored.getString("householdId"));
        assertFalse(stored.getData().containsValue(code));
        mvc.perform(post("/api/invitations/redeem").contentType("application/json")
                .content("{\"code\":\""+code+"\"}").header("Authorization", "Bearer "+token(roommate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.householdId").value(home.id()))
                .andExpect(jsonPath("$.status").value("joined"));
        mvc.perform(get("/api/households").header("Authorization", "Bearer "+token(roommate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(home.id()))
                .andExpect(jsonPath("$[0].members.length()").value(2));
        assertEquals(home.id(), access.requireMember(home.id(), roommate).getId());
    }

    @Test void rejectsMissingAndExpiredCodesWithoutCreatingMembership() throws Exception {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> invitations.redeem("invalid", roommate)).getStatusCode().value());
        mvc.perform(post("/api/invitations/redeem").contentType("application/json")
                .content("{\"code\":\""+"A".repeat(43)+"\"}").header("Authorization", "Bearer "+token(roommate)))
                .andExpect(status().isNotFound());
        var past = new InvitationService(firestore, access, Clock.fixed(Instant.now().minus(Duration.ofDays(3)), ZoneOffset.UTC));
        String expired = past.create(home.id(), owner).code();
        mvc.perform(post("/api/invitations/redeem").contentType("application/json")
                .content("{\"code\":\""+expired+"\"}").header("Authorization", "Bearer "+token(roommate)))
                .andExpect(status().isGone());
        assertTrue(households.list(roommate).isEmpty());
        assertEquals(1, firestore.collection("households").document(home.id()).collection("members")
                .get().get(10, TimeUnit.SECONDS).size());
    }


    // Manual test created during AI Integration in Testing assignment - Robert Ives
    // loosely based upon rejectsMissingAndExpiredCodesWithoutCreatingMembership(), but testing the case where the expiry is exactly the current time, which should fail
    @Test void rejectsCodeAtExactExpiryWithoutCreatingMembership() throws Exception {
        Instant issuedAt = Instant.parse("2030-01-01T00:00:00Z");
        var issuer = new InvitationService(firestore, access, Clock.fixed(issuedAt, ZoneOffset.UTC));

        var invite = issuer.create(home.id(), owner);

        var redeemer = new InvitationService(firestore, access, Clock.fixed(invite.expiresAt(), ZoneOffset.UTC));

        var failure = assertThrows(ResponseStatusException.class, () -> redeemer.redeem(invite.code(), roommate));

        assertEquals(410, failure.getStatusCode().value());
        assertTrue(households.list(roommate).isEmpty());
        assertEquals(1, firestore.collection("households")
                .document(home.id())
                .collection("members")
                .get().get(10, TimeUnit.SECONDS)
                .size());
    }

    @Test void concurrentRedemptionCreatesOnlyOneMembership() throws Exception {
        String code = invitations.create(home.id(), owner).code();
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<InvitationService.RedeemedInvitation>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) calls.add(() -> invitations.redeem(code, roommate));
            var outcomes = new ArrayList<InvitationService.RedeemedInvitation>();
            for (var future : executor.invokeAll(calls)) outcomes.add(future.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(result -> result.status().equals("joined")).count());
            assertEquals(7, outcomes.stream().filter(result -> result.status().equals("already_member")).count());
        }
        var reference = firestore.collection("households").document(home.id());
        assertEquals(2, reference.collection("members").get().get(10, TimeUnit.SECONDS).size());
        assertEquals(2, ((List<?>)reference.get().get(10, TimeUnit.SECONDS).get("memberIds")).size());
    }

    @Test void validCodeCanBeReusedByDifferentRoommatesUntilExpiry() {
        var invitation = invitations.create(home.id(), owner);
        assertTrue(invitation.expiresAt().isAfter(Instant.now().plus(Duration.ofHours(47))));
        assertEquals("joined", invitations.redeem(invitation.code(), roommate).status());
        assertEquals("joined", invitations.redeem(invitation.code(), "second/"+UUID.randomUUID()).status());
        assertEquals("already_member", invitations.redeem(invitation.code(), owner).status());
    }
}
