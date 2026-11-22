package edu.group17.roommates.expense;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@WebMvcTest(controllers = ExpenseController.class, properties = "app.firestore.enabled=true")
@Import({SecurityConfig.class, FirebaseJwtConfiguration.class, TestIdentityConfiguration.class})
class ExpenseControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExpenseService service;

    @Test void postsCalculatedExpenseAndUsesAuthenticatedSubjectAsCreator() throws Exception {
        when(service.create(eq("home"), eq("alice"), any())).thenAnswer(call -> {
            var request = (ExpenseService.CreateExpense) call.getArgument(2);
            return new Expense("expense-id", "home", request.description(), request.amountCents(), request.currency(),
                    request.payerId(), request.participantIds(), request.expenseDate(), request.category(),
                    request.splitType(), Map.of("alice", 51L, "bob", 50L), "alice", Instant.parse("2026-11-18T12:00:00Z"));
        });
        mvc.perform(post("/api/households/home/expenses?createdBy=forged")
                        .header("Authorization", "Bearer " + token("alice"))
                        .contentType("application/json")
                        .content("""
                                {"description":"Power","amountCents":101,"currency":"USD","payerId":"alice",
                                 "participantIds":["alice","bob"],"expenseDate":"2026-11-17","category":"UTILITIES",
                                 "splitType":"EQUAL","createdBy":"forged","sharesCents":{"alice":101}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andExpect(jsonPath("$.sharesCents.alice").value(51))
                .andExpect(jsonPath("$.sharesCents.bob").value(50));
        var request = org.mockito.ArgumentCaptor.forClass(ExpenseService.CreateExpense.class);
        verify(service).create(eq("home"), eq("alice"), request.capture());
        org.junit.jupiter.api.Assertions.assertEquals("alice", request.getValue().payerId());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("alice", "bob"), request.getValue().participantIds());
    }

    @Test void rejectsUnauthenticatedAndInvalidRequestsBeforeServiceCalls() throws Exception {
        mvc.perform(post("/api/households/home/expenses")).andExpect(status().isUnauthorized());
        for (String body : List.of("{}", "{\"description\":\"Rent\",\"amountCents\":0}",
                "{\"description\":\"Rent\",\"amountCents\":100,\"currency\":\"usd\"}")) {
            mvc.perform(post("/api/households/home/expenses").contentType("application/json").content(body)
                            .header("Authorization", "Bearer " + token("alice")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void returnsHistoryPagesAndExpenseDetailsForTheAuthenticatedMember() throws Exception {
        var expense = new Expense("expense-id", "home", "Power", 101L, "USD", "alice",
                List.of("alice", "bob"), LocalDate.parse("2026-11-17"), ExpenseCategory.UTILITIES,
                SplitType.EQUAL, Map.of("alice", 51L, "bob", 50L), "alice",
                Instant.parse("2026-11-18T12:00:00Z"));
        when(service.list("home", "alice", 2, "opaque-cursor"))
                .thenReturn(new ExpensePage(List.of(expense), "next-cursor"));
        mvc.perform(get("/api/households/home/expenses").param("limit", "2").param("cursor", "opaque-cursor")
                        .header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("expense-id"))
                .andExpect(jsonPath("$.items[0].payerId").value("alice"))
                .andExpect(jsonPath("$.items[0].sharesCents.bob").value(50))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"));
        verify(service).list("home", "alice", 2, "opaque-cursor");

        when(service.find("home", "alice", "expense-id")).thenReturn(expense);
        mvc.perform(get("/api/households/home/expenses/expense-id")
                        .header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Power"))
                .andExpect(jsonPath("$.amountCents").value(101))
                .andExpect(jsonPath("$.category").value("UTILITIES"))
                .andExpect(jsonPath("$.expenseDate").value("2026-11-17"));
        verify(service).find("home", "alice", "expense-id");
    }

    @Test void returnsEmptyHistoryAndHidesMissingExpense() throws Exception {
        when(service.list("home", "alice", ExpenseService.DEFAULT_HISTORY_LIMIT, null))
                .thenReturn(new ExpensePage(List.of(), null));
        mvc.perform(get("/api/households/home/expenses").header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        when(service.find("home", "alice", "missing"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/households/home/expenses/missing")
                        .header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isNotFound());
    }
}
