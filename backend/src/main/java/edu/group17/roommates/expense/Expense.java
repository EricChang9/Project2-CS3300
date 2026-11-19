package edu.group17.roommates.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Persisted expense with server-calculated participant shares, all amounts in cents. */
public record Expense(String id, String householdId, String description, long amountCents, String currency,
                      String payerId, List<String> participantIds, LocalDate expenseDate,
                      ExpenseCategory category, SplitType splitType, Map<String, Long> sharesCents,
                      String createdBy, Instant createdAt) {
    public Expense {
        participantIds = List.copyOf(participantIds);
        sharesCents = Map.copyOf(sharesCents);
    }
}
