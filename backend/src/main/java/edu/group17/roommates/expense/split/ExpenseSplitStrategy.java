package edu.group17.roommates.expense.split;

import java.util.Map;

/**
 * Calculates each participant's share of an expense.
 *
 * @param <T> the input required by the splitting strategy
 */
@FunctionalInterface
public interface ExpenseSplitStrategy<T> {
    /**
     * Splits a non-negative expense, expressed in cents, among participants.
     *
     * @param totalCents total expense amount in cents
     * @param splitDetails strategy-specific participant details
     * @return participant IDs and their shares in cents
     * @throws IllegalArgumentException if the total or details are invalid
     */
    Map<String, Long> split(long totalCents, T splitDetails);
}
