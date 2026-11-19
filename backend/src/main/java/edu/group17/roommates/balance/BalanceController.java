package edu.group17.roommates.balance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class BalanceController {
    private final BalanceService service;

    public BalanceController(BalanceService service) { this.service = service; }

    /**
     * Returns net directed obligations and personal totals for each currency.
     * Recorded settlements reduce stored obligations before opposite debts are offset; positive net means owed.
     */
    @GetMapping("/api/households/{householdId}/balances")
    public HouseholdBalance get(@PathVariable String householdId, @AuthenticationPrincipal Jwt identity) {
        return service.get(householdId, identity.getSubject());
    }
}
