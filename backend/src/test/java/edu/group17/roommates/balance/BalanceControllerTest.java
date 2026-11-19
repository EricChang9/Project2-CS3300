package edu.group17.roommates.balance;

import edu.group17.roommates.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BalanceController.class, properties = "app.firestore.enabled=true")
@Import(SecurityConfig.class)
class BalanceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BalanceService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void returnsDirectedDebtsAndPersonalTotalsForTheAuthenticatedMember() throws Exception {
        when(service.get("home", "alice")).thenReturn(new HouseholdBalance("home",
                List.of(new HouseholdBalance.DirectedDebt("bob", "alice", "USD", 325)),
                List.of(new HouseholdBalance.MemberBalance("alice", Map.of("USD",
                                new HouseholdBalance.PersonalTotals(0, 325, 325))),
                        new HouseholdBalance.MemberBalance("bob", Map.of("USD",
                                new HouseholdBalance.PersonalTotals(325, 0, -325)))),
                List.of(new HouseholdBalance.CurrencyTotal("USD", 325, 325))));

        mvc.perform(get("/api/households/home/balances").with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value("home"))
                .andExpect(jsonPath("$.debts[0].debtorId").value("bob"))
                .andExpect(jsonPath("$.debts[0].creditorId").value("alice"))
                .andExpect(jsonPath("$.debts[0].amountCents").value(325))
                .andExpect(jsonPath("$.members[0].byCurrency.USD.netBalanceCents").value(325))
                .andExpect(jsonPath("$.totals[0].totalOwingCents").value(325))
                .andExpect(jsonPath("$.totals[0].totalOwedCents").value(325));
        verify(service).get("home", "alice");
    }

    @Test void requiresAuthenticationAndHidesHouseholdsTheCallerCannotRead() throws Exception {
        mvc.perform(get("/api/households/home/balances")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);

        when(service.get("other-home", "alice")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Household resource not found"));
        mvc.perform(get("/api/households/other-home/balances").with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isNotFound());
        verify(service).get("other-home", "alice");
    }
}
