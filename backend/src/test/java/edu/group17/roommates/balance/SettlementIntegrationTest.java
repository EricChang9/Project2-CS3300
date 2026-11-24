package edu.group17.roommates.balance;

import com.google.cloud.firestore.Firestore;
import edu.group17.roommates.household.Household;
import edu.group17.roommates.household.HouseholdService;
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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@SpringBootTest(properties = {"app.firestore.enabled=true", "app.firestore.project-id=demo-roommate-expenses"})
@AutoConfigureMockMvc
@Import(TestIdentityConfiguration.class)
class SettlementIntegrationTest {
    @Autowired Firestore firestore;
    @Autowired HouseholdService households;
    @Autowired InvitationService invitations;
    @Autowired MockMvc mvc;

    private final String owner = "settlement-owner/" + UUID.randomUUID();
    private final String roommate = "settlement-roommate/" + UUID.randomUUID();
    private final String outsider = "settlement-outsider/" + UUID.randomUUID();
    private Household home;

    @BeforeEach void setup() {
        home = households.create("Settlement test household", owner);
        invitations.redeem(invitations.create(home.id(), owner).code(), roommate);
    }

    @AfterEach void cleanup() throws Exception {
        if (home == null) return;
        var reference = firestore.collection("households").document(home.id());
        for (var expense : reference.collection("expenses").get().get(10, TimeUnit.SECONDS).getDocuments()) {
            for (var share : expense.getReference().collection("shares").get().get(10, TimeUnit.SECONDS).getDocuments()) {
                share.getReference().delete().get(10, TimeUnit.SECONDS);
            }
            expense.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        for (var settlement : reference.collection("settlements").get().get(10, TimeUnit.SECONDS).getDocuments()) {
            settlement.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        for (var member : reference.collection("members").get().get(10, TimeUnit.SECONDS).getDocuments()) {
            member.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        for (var invitation : firestore.collection("householdInvitations").whereEqualTo("householdId", home.id())
                .get().get(10, TimeUnit.SECONDS).getDocuments()) {
            invitation.getReference().delete().get(10, TimeUnit.SECONDS);
        }
        reference.delete().get(10, TimeUnit.SECONDS);
    }

    private void addExpense() throws Exception {
        mvc.perform(post("/api/households/" + home.id() + "/expenses")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json")
                        .content("""
                                {"description":"Groceries","amountCents":1000,"currency":"USD",
                                 "payerId":"%s","participantIds":["%s","%s"],
                                 "expenseDate":"2026-11-23","category":"GROCERIES","splitType":"EQUAL"}
                                """.formatted(roommate, owner, roommate)))
                .andExpect(status().isCreated());
    }

    private String payment(String id, long cents) {
        return """
                {"toUid":"%s","currency":"USD","amountCents":%d,"requestId":"%s"}
                """.formatted(roommate, cents, id);
    }

    @Test void recordsPaymentOnceAndReducesOutstandingBalance() throws Exception {
        addExpense();
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/households/" + home.id() + "/balances")
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debts[0].amountCents").value(500));

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/households/" + home.id() + "/settlements")
                            .header("Authorization", "Bearer " + token(owner))
                            .contentType("application/json").content(payment(id, 500)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fromUid").value(owner))
                    .andExpect(jsonPath("$.toUid").value(roommate))
                    .andExpect(jsonPath("$.amountCents").value(500));
        }
        mvc.perform(get("/api/households/" + home.id() + "/balances")
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debts.length()").value(0));
        assertEquals(1, firestore.collection("households").document(home.id()).collection("settlements")
                .get().get(10, TimeUnit.SECONDS).size());

        mvc.perform(post("/api/households/" + home.id() + "/settlements")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json").content(payment(UUID.randomUUID().toString(), 500)))
                .andExpect(status().isConflict());
    }

    @Test void rejectsOverpaymentNonMembersAndInvalidAmounts() throws Exception {
        addExpense();
        var path = "/api/households/" + home.id() + "/settlements";
        mvc.perform(post(path).header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json").content(payment(UUID.randomUUID().toString(), 501)))
                .andExpect(status().isConflict());
        mvc.perform(post(path).header("Authorization", "Bearer " + token(outsider))
                        .contentType("application/json").content(payment(UUID.randomUUID().toString(), 500)))
                .andExpect(status().isNotFound());
        mvc.perform(post(path).header("Authorization", "Bearer " + token(owner))
                        .contentType("application/json").content(payment(UUID.randomUUID().toString(), -1)))
                .andExpect(status().isBadRequest());
        assertEquals(0, firestore.collection("households").document(home.id()).collection("settlements")
                .get().get(10, TimeUnit.SECONDS).size());
    }
}
