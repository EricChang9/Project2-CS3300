package edu.group17.roommates.expense;

import edu.group17.roommates.expense.split.CustomSplitStrategy;
import edu.group17.roommates.expense.split.EqualSplitStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.util.*;

@Service
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class ExpenseService {
    private static final int MAX_PARTICIPANTS = 499; // one expense + at most 499 share writes per transaction
    private final ExpenseStore store;
    private final Clock clock;
    private final EqualSplitStrategy equalSplit = new EqualSplitStrategy();
    private final CustomSplitStrategy customSplit = new CustomSplitStrategy();

    public ExpenseService(ExpenseStore store) {
        this(store, Clock.systemUTC());
    }

    ExpenseService(ExpenseStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Expense create(String householdId, String callerUid, CreateExpense request) {
        if (callerUid == null || callerUid.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (householdId == null || !householdId.matches("[A-Za-z0-9_-]{1,128}")) throw notFound();
        if (request == null || request.description() == null || request.description().isBlank()
                || request.description().length() > 200 || request.amountCents() == null || request.amountCents() <= 0
                || request.currency() == null || !request.currency().matches("[A-Z]{3}")
                || request.payerId() == null || request.payerId().isBlank()
                || request.participantIds() == null || request.participantIds().isEmpty()
                || request.participantIds().size() > MAX_PARTICIPANTS || request.expenseDate() == null
                || request.category() == null || request.splitType() == null) {
            throw new IllegalArgumentException("Invalid expense fields");
        }
        Currency currency;
        try {
            currency = Currency.getInstance(request.currency());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency must be an ISO 4217 code", exception);
        }
        if (currency.getDefaultFractionDigits() != 2) {
            throw new IllegalArgumentException("Currency must use two minor-unit digits");
        }
        if (request.participantIds().stream().anyMatch(uid -> uid == null || uid.isBlank())
                || new HashSet<>(request.participantIds()).size() != request.participantIds().size()) {
            throw new IllegalArgumentException("Participant IDs must be nonblank and unique");
        }
        var participantIds = List.copyOf(request.participantIds());

        Map<String, Long> shares;
        if (request.splitType() == SplitType.EQUAL) {
            if (request.customAllocations() != null) throw new IllegalArgumentException("Equal split cannot include custom allocations");
            shares = equalSplit.split(request.amountCents(), participantIds);
        } else {
            if (request.customAllocations() == null
                    || !request.customAllocations().keySet().equals(new HashSet<>(participantIds))) {
                throw new IllegalArgumentException("Custom allocations must match the participants");
            }
            shares = customSplit.split(request.amountCents(), request.customAllocations());
        }

        var expense = new Expense(UUID.randomUUID().toString(), householdId, request.description().strip(),
                request.amountCents(), request.currency(), request.payerId(), participantIds, request.expenseDate(),
                request.category(), request.splitType(), shares, callerUid, clock.instant());
        return store.create(expense);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Household resource not found");
    }

    public record CreateExpense(String description, Long amountCents, String currency, String payerId,
                                List<String> participantIds, java.time.LocalDate expenseDate,
                                ExpenseCategory category, SplitType splitType,
                                Map<String, Long> customAllocations) {}
}
