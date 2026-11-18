package edu.group17.roommates.account;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.ObjectProvider;

@RestController
public class CurrentUserController {
    private final ObjectProvider<UserProfileStore> profiles;
    public CurrentUserController(ObjectProvider<UserProfileStore> profiles) { this.profiles = profiles; }

    @GetMapping("/api/me")
    public CurrentUser currentUser(@AuthenticationPrincipal Jwt identity) {
        var store = profiles.getIfAvailable();
        if (store != null) store.sync(identity.getSubject(), identity.getClaimAsString("name"), identity.getClaimAsString("email"));
        return new CurrentUser(identity.getSubject(), identity.getClaimAsString("email"),
                identity.getClaimAsString("name"));
    }

    public record CurrentUser(String uid, String email, String displayName) {}
}
