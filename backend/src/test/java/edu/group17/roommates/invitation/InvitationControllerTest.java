package edu.group17.roommates.invitation;

import edu.group17.roommates.config.*;
import edu.group17.roommates.support.TestIdentityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static edu.group17.roommates.support.TestIdentityConfiguration.token;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InvitationController.class, properties = "app.firestore.enabled=true")
@Import({SecurityConfig.class, FirebaseJwtConfiguration.class, TestIdentityConfiguration.class})
class InvitationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InvitationService service;

    @Test void createsUsingVerifiedIdentityAndMatchesTheFrontendContract() throws Exception {
        when(service.create("home", "alice")).thenReturn(new InvitationService.CreatedInvitation(
                "A".repeat(43), Instant.parse("2030-01-01T00:00:00Z")));
        mvc.perform(post("/api/households/home/invitations?uid=forged")
                .header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("A".repeat(43)))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"));
        verify(service).create("home", "alice");
    }

    @Test void rejectsUnauthenticatedAndMalformedRequestsBeforeServiceCalls() throws Exception {
        mvc.perform(post("/api/households/home/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/invitations/redeem").contentType("application/json")
                .content("{\"code\":\""+"A".repeat(43)+"\"}")).andExpect(status().isUnauthorized());
        for (String body : new String[]{"{}", "{\"code\":\"../bad\"}", "{\"code\":\" \"}"}) {
            mvc.perform(post("/api/invitations/redeem").contentType("application/json").content(body)
                    .header("Authorization", "Bearer " + token("alice"))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void returnsExistingMembershipResultWithoutTrustingBodyIdentity() throws Exception {
        String code = "A".repeat(43);
        when(service.redeem(code, "alice")).thenReturn(new InvitationService.RedeemedInvitation("home", "already_member"));
        mvc.perform(post("/api/invitations/redeem").contentType("application/json")
                .content("{\"code\":\""+code+"\",\"uid\":\"forged\"}")
                .header("Authorization", "Bearer " + token("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.householdId").value("home"))
                .andExpect(jsonPath("$.status").value("already_member"));
        verify(service).redeem(code, "alice");
    }
}
