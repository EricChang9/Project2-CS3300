package edu.group17.roommates.expense;

import com.google.cloud.firestore.Firestore;
import edu.group17.roommates.household.Household;
import edu.group17.roommates.household.HouseholdService;
import edu.group17.roommates.household.HouseholdStorageException;
import edu.group17.roommates.invitation.InvitationService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@SpringBootTest(properties = {"app.firestore.enabled=true", "app.firestore.project-id=demo-roommate-expenses"})
@AutoConfigureMockMvc
@Import(TestIdentityConfiguration.class)
class ExpenseIntegrationTest {
    @Autowired Firestore firestore;
    @Autowired HouseholdService households;
    @Autowired InvitationService invitations;
    @Autowired ExpenseStore expenses;
    @Autowired MockMvc mvc;

    private final String owner = "expense-owner/" + UUID.randomUUID();
    private final String roommate = "expense-roommate/" + UUID.randomUUID();
    private final String thirdMember = "expense-third/" + UUID.randomUUID();
    private final String outsider = "expense-outsider/" + UUID.randomUUID();
    private Household home;
    private Household otherHome;

    @BeforeEach void createHouseholdAndMembers() {
        home = households.create("Expense integration home", owner);
        invitations.redeem(invitations.create(home.id(), owner).code(), roommate);
        invitations.redeem(invitations.create(home.id(), owner).code(), thirdMember);
    }

    @AfterEach void cleanOnlyThisTestDocuments() throws Exception {
        if (home != null) cleanHousehold(home);
        if (otherHome != null) cleanHousehold(otherHome);
    }

    private void cleanHousehold(Household household) throws Exception {
        var reference = firestore.collection("households").document(household.id());
        for (var expense : reference.collection("expenses").get().get(10, TimeUnit.SECONDS).getDocuments()) {
            for (var share : expense.getReference().collection("shares").get().get(10, TimeUnit.SECONDS).getDocuments()) {
                share.getReference().delete().get(10, TimeUnit.SECONDS);
            }
            expense.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        for (var member : reference.collection("members").get().get(10, TimeUnit.SECONDS).getDocuments()) {
            member.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        for (var invitation : firestore.collection("householdInvitations").whereEqualTo("householdId", household.id())
                .get().get(10, TimeUnit.SECONDS).getDocuments()) {
            invitation.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        reference.delete().get(10, TimeUnit.SECONDS);
    }

    @Test void storesCalculatedSharesAndRollsBackTheExpenseAndAllSharesWhenAWriteFails() throws Exception {
        var result = mvc.perform(post("/api/households/" + home.id() + "/expenses")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json")
                        .content("""
                                {"description":"Electricity","amountCents":101,"currency":"USD","payerId":"%s",
                                 "participantIds":["%s","%s"],"expenseDate":"2026-11-17","category":"UTILITIES",
                                 "splitType":"EQUAL","createdBy":"forged","sharesCents":{"%s":101}}
                                """.formatted(roommate, owner, roommate, owner)))
                .andExpect(status().isCreated()).andReturn();
        var response = com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString());
        String expenseId = response.read("$.id");
        assertEquals(owner, (String) response.read("$.createdBy"));
        assertEquals(51, ((Number) response.read("$.sharesCents['" + owner + "']")).longValue());
        assertEquals(50, ((Number) response.read("$.sharesCents['" + roommate + "']")).longValue());

        var expenseRef = firestore.collection("households").document(home.id()).collection("expenses").document(expenseId);
        var stored = expenseRef.get().get(10, TimeUnit.SECONDS);
        assertEquals(101L, stored.getLong("amountCents"));
        assertEquals("USD", stored.getString("currency"));
        assertEquals(roommate, stored.getString("payerId"));
        assertEquals(owner, stored.getString("createdBy"));
        assertEquals("2026-11-17", stored.getString("expenseDate"));
        assertEquals("UTILITIES", stored.getString("category"));
        assertEquals("EQUAL", stored.getString("splitType"));
        assertNotNull(stored.getTimestamp("createdAt"));
        var savedShares = expenseRef.collection("shares").get().get(10, TimeUnit.SECONDS).getDocuments();
        assertEquals(2, savedShares.size());
        assertEquals(101L, savedShares.stream().mapToLong(doc -> doc.getLong("amountCents")).sum());

        mvc.perform(post("/api/households/" + home.id() + "/expenses")
                        .header("Authorization", "Bearer " + token(outsider))
                        .contentType("application/json")
                        .content("""
                                {"description":"Unauthorized","amountCents":100,"currency":"USD","payerId":"%s",
                                 "participantIds":["%s"],"expenseDate":"2026-11-17","category":"OTHER","splitType":"EQUAL"}
                                """.formatted(outsider, outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/households/" + home.id() + "/expenses")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json")
                        .content("""
                                {"description":"Unknown payer","amountCents":100,"currency":"USD","payerId":"%s",
                                 "participantIds":["%s"],"expenseDate":"2026-11-17","category":"OTHER","splitType":"EQUAL"}
                                """.formatted(outsider, owner)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/households/" + home.id() + "/expenses")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json")
                        .content("""
                                {"description":"Unknown participant","amountCents":100,"currency":"USD","payerId":"%s",
                                 "participantIds":["%s"],"expenseDate":"2026-11-17","category":"OTHER","splitType":"EQUAL"}
                                """.formatted(owner, outsider)))
                .andExpect(status().isNotFound());
        assertEquals(1, firestore.collection("households").document(home.id()).collection("expenses")
                .get().get(10, TimeUnit.SECONDS).size());

        var failed = new Expense(expenseId, home.id(), "collision", 101L, "USD", owner,
                List.of(owner, thirdMember), LocalDate.parse("2026-11-17"), ExpenseCategory.OTHER, SplitType.EQUAL,
                Map.of(owner, 51L, thirdMember, 50L), owner, Instant.parse("2026-11-18T12:00:00Z"));
        assertThrows(HouseholdStorageException.class, () -> expenses.create(failed));
        assertFalse(expenseRef.collection("shares").document(memberId(thirdMember)).get()
                .get(10, TimeUnit.SECONDS).exists());
        assertEquals(51L, expenseRef.collection("shares").document(memberId(owner)).get()
                .get(10, TimeUnit.SECONDS).getLong("amountCents"));
        assertEquals(1, firestore.collection("households").document(home.id()).collection("expenses")
                .get().get(10, TimeUnit.SECONDS).size());
    }

    @Test void pagesHistoryInStableOrderAndHidesMissingOrCrossHouseholdExpenses() throws Exception {
        mvc.perform(get("/api/households/" + home.id() + "/expenses")
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items.length()").value(0));

        String older = createExpense(home.id(), "Older", "2026-11-17");
        String newerA = createExpense(home.id(), "Newer A", "2026-11-18");
        String newerB = createExpense(home.id(), "Newer B", "2026-11-18");
        otherHome = households.create("Other expense home", owner);
        String otherExpense = createExpense(otherHome.id(), "Other home", "2026-11-18");

        var firstPage = mvc.perform(get("/api/households/" + home.id() + "/expenses")
                        .param("limit", "2").header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items.length()").value(2)).andReturn();
        var pageJson = com.jayway.jsonpath.JsonPath.parse(firstPage.getResponse().getContentAsString());
        @SuppressWarnings("unchecked")
        List<String> firstIds = pageJson.read("$.items[*].id");
        var sameDateIds = List.of(newerA, newerB).stream().sorted(Comparator.reverseOrder()).toList();
        assertEquals(sameDateIds, firstIds);
        String cursor = pageJson.read("$.nextCursor");
        assertNotNull(cursor);
        assertFalse(cursor.isBlank());

        var secondPage = mvc.perform(get("/api/households/" + home.id() + "/expenses")
                        .param("limit", "2").param("cursor", cursor)
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items.length()").value(1)).andReturn();
        assertEquals(List.of(older), com.jayway.jsonpath.JsonPath.parse(secondPage.getResponse().getContentAsString())
                .read("$.items[*].id"));
        assertNull(com.jayway.jsonpath.JsonPath.parse(secondPage.getResponse().getContentAsString())
                .read("$.nextCursor"));

        mvc.perform(get("/api/households/" + home.id() + "/expenses/" + newerA)
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.description").value("Newer A"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.sharesCents['" + owner + "']").value(123));
        mvc.perform(get("/api/households/" + home.id() + "/expenses/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/households/" + home.id() + "/expenses/" + otherExpense)
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isNotFound());
    }

    private String createExpense(String householdId, String description, String date) throws Exception {
        var response = mvc.perform(post("/api/households/" + householdId + "/expenses")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json")
                        .content("""
                                {"description":"%s","amountCents":123,"currency":"USD","payerId":"%s",
                                 "participantIds":["%s"],"expenseDate":"%s","category":"OTHER","splitType":"EQUAL"}
                                """.formatted(description, owner, owner, date)))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.id");
    }

    private static String memberId(String uid) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
