package edu.group17.roommates.account;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {
    @GetMapping("/api/me")
    public CurrentUser currentUser(@AuthenticationPrincipal Jwt identity) {
        return new CurrentUser(identity.getSubject(), identity.getClaimAsString("email"),
                identity.getClaimAsString("name"));
    }

    public record CurrentUser(String uid, String email, String displayName) {}
}
