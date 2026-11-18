package edu.group17.roommates.household;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/households")
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class HouseholdController {
    private final HouseholdService service;
    public HouseholdController(HouseholdService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Household create(@Valid @RequestBody CreateHousehold request, @AuthenticationPrincipal Jwt identity) {
        return service.create(request.name(), identity.getSubject());
    }

    @GetMapping
    public List<Household> list(@AuthenticationPrincipal Jwt identity) {
        return service.list(identity.getSubject());
    }

    public record CreateHousehold(@NotBlank @Size(max = 80) String name) {}
}
