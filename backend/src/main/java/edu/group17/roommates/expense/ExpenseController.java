package edu.group17.roommates.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) { this.service = service; }

    @PostMapping("/api/households/{householdId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public Expense create(@PathVariable String householdId, @Valid @RequestBody CreateExpenseRequest request,
                          @AuthenticationPrincipal Jwt identity) {
        return service.create(householdId, identity.getSubject(), new ExpenseService.CreateExpense(
                request.description(), request.amountCents(), request.currency(), request.payerId(),
                request.participantIds(), request.expenseDate(), request.category(), request.splitType(),
                request.customAllocations()));
    }

    @GetMapping("/api/households/{householdId}/expenses")
    public ExpensePage list(@PathVariable String householdId,
                            @RequestParam(defaultValue = "" + ExpenseService.DEFAULT_HISTORY_LIMIT) int limit,
                            @RequestParam(required = false) String cursor,
                            @AuthenticationPrincipal Jwt identity) {
        return service.list(householdId, identity.getSubject(), limit, cursor);
    }

    @GetMapping("/api/households/{householdId}/expenses/{expenseId}")
    public Expense find(@PathVariable String householdId, @PathVariable String expenseId,
                        @AuthenticationPrincipal Jwt identity) {
        return service.find(householdId, identity.getSubject(), expenseId);
    }

    public record CreateExpenseRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @Positive Long amountCents,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Size(max = 128) String payerId,
            @NotEmpty @Size(max = 499) List<@NotBlank @Size(max = 128) String> participantIds,
            @NotNull LocalDate expenseDate,
            @NotNull ExpenseCategory category,
            @NotNull SplitType splitType,
            Map<String, Long> customAllocations) {}
}
