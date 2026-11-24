package edu.group17.roommates.balance;

import edu.group17.roommates.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SettlementController.class, properties = "app.firestore.enabled=true")
@Import(SecurityConfig.class)
class SettlementControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SettlementService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final String REQUEST = """
            {"toUid":"bob","currency":"USD","amountCents":250,
             "requestId":"aa99f2ad-9d26-4ea4-a885-60dff731d2a2"}
            """;

    @Test void usesAuthenticatedUserForRecordedPayment() throws Exception {
        when(service.record("home", "alice", "bob", "USD", 250,
                "aa99f2ad-9d26-4ea4-a885-60dff731d2a2"))
                .thenReturn(new SettlementService.SettlementReceipt("aa99f2ad-9d26-4ea4-a885-60dff731d2a2",
                        "home", "alice", "bob", "USD", 250));
        mvc.perform(post("/api/households/home/settlements")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .jwt().jwt(token -> token.subject("alice")))
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromUid").value("alice"))
                .andExpect(jsonPath("$.toUid").value("bob"))
                .andExpect(jsonPath("$.amountCents").value(250));
        verify(service).record("home", "alice", "bob", "USD", 250,
                "aa99f2ad-9d26-4ea4-a885-60dff731d2a2");
    }

    @Test void rejectsUnauthenticatedAndInvalidPaymentRequests() throws Exception {
        mvc.perform(post("/api/households/home/settlements")
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/households/home/settlements")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .jwt().jwt(token -> token.subject("alice")))
                        .contentType("application/json")
                        .content("""
                                {"toUid":"bob","currency":"USD","amountCents":-1,
                                 "requestId":"aa99f2ad-9d26-4ea4-a885-60dff731d2a2"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/households/home/settlements")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .jwt().jwt(token -> token.subject("alice")))
                        .contentType("application/json")
                        .content("""
                                {"toUid":"bob","currency":"USD","amountCents":2.5,
                                 "requestId":"aa99f2ad-9d26-4ea4-a885-60dff731d2a2"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
