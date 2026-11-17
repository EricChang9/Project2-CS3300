package edu.group17.roommates.account;

import edu.group17.roommates.support.TestIdentityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestIdentityConfiguration.class)
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void identityComesFromVerifiedTokenNotQueryParameters() throws Exception {
        mvc.perform(get("/api/me?uid=someone-else").header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uid").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.displayName").value("Test user"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void missingMalformedAndForgedCredentialsAreRejected() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        for (String value : new String[]{"not-a-jwt", token("alice", Map.of(), true)}) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + value))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rejectsExpiredFutureWrongIssuerAndWrongAudienceTokens() throws Exception {
        var invalidClaims = java.util.List.<Map<String, Object>>of(
                Map.of("exp", Date.from(Instant.now().minusSeconds(120))),
                Map.of("iat", Date.from(Instant.now().plusSeconds(120))),
                Map.of("iss", "https://attacker.example"),
                Map.of("aud", "other-project"),
                Map.of("auth_time", Instant.now().plusSeconds(120).getEpochSecond()),
                Map.of("auth_time", "invalid"),
                java.util.Collections.singletonMap("exp", null),
                java.util.Collections.singletonMap("iat", null));
        for (var claims : invalidClaims) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + token("alice", claims, false)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token(" ")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statelessBearerPolicyDoesNotEnableCookiesBasicAuthOrCrossOriginRequests() throws Exception {
        mvc.perform(get("/api/me").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "fake")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Basic YWxpY2U6cGFzcw=="))
                .andExpect(status().isUnauthorized());
        mvc.perform(options("/api/me").header("Origin", "https://attacker.example")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(post("/api/me").header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isForbidden());
    }
}
