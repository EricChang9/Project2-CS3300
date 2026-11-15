package edu.group17.roommates.expense.split;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses explicitly provided participant shares, expressed in cents. */
public final class CustomSplitStrategy implements ExpenseSplitStrategy<Map<String, Long>> {
    @Override
    public Map<String, Long> split(long totalCents, Map<String, Long> allocations) {
        if (totalCents < 0) {
            throw new IllegalArgumentException("Expense total cannot be negative");
        }
        if (allocations == null || allocations.isEmpty()) {
            throw new IllegalArgumentException("Allocations cannot be null or empty");
        }

        Map<String, Long> shares = new LinkedHashMap<>();
        long allocatedCents = 0;
        for (Map.Entry<String, Long> allocation : allocations.entrySet()) {
            String participant = allocation.getKey();
            Long amount = allocation.getValue();
            if (participant == null || participant.isBlank()) {
                throw new IllegalArgumentException("Participant IDs cannot be null or blank");
            }
            if (amount == null || amount < 0) {
                throw new IllegalArgumentException("Allocation amounts cannot be null or negative");
            }
            try {
                allocatedCents = Math.addExact(allocatedCents, amount);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Allocation total is too large", exception);
            }
            shares.put(participant, amount);
        }
        if (allocatedCents != totalCents) {
            throw new IllegalArgumentException("Allocation amounts must equal the expense total");
        }
        return Collections.unmodifiableMap(shares);
    }
}
