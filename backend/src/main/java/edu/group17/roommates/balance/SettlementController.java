package edu.group17.roommates.balance;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class SettlementController {
    private final SettlementService service;

    public SettlementController(SettlementService service) { this.service = service; }

    @PostMapping("/api/households/{householdId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementService.SettlementReceipt record(@PathVariable String householdId,
            @Valid @RequestBody RecordSettlementRequest request, @AuthenticationPrincipal Jwt identity) {
        long cents;
        try { cents = request.amountCents().longValueExact(); }
        catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be whole cents");
        }
        return service.record(householdId, identity.getSubject(), request.toUid(), request.currency(),
                cents, request.requestId());
    }

    public record RecordSettlementRequest(
            @NotBlank String toUid,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @Positive BigDecimal amountCents,
            @NotBlank @Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}") String requestId) {}
}
