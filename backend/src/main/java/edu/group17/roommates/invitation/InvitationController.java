package edu.group17.roommates.invitation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class InvitationController {
    private final InvitationService service;
    public InvitationController(InvitationService service) { this.service = service; }

    @PostMapping("/api/households/{householdId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationService.CreatedInvitation create(@PathVariable String householdId, @AuthenticationPrincipal Jwt user) {
        return service.create(householdId, user.getSubject());
    }

    @PostMapping("/api/invitations/redeem")
    public InvitationService.RedeemedInvitation redeem(@Valid @RequestBody RedeemRequest request,
                                                      @AuthenticationPrincipal Jwt user) {
        return service.redeem(request.code(), user.getSubject());
    }

    public record RedeemRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String code) {}
}
