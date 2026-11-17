package edu.group17.roommates.expense.split;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseSplitStrategyTest {
    private final EqualSplitStrategy equalSplit = new EqualSplitStrategy();
    private final CustomSplitStrategy customSplit = new CustomSplitStrategy();

    @Test
    void splitsEqualAmountsExactly() {
        Map<String, Long> shares = equalSplit.split(900, List.of("alex", "sam", "lee"));

        assertEquals(Map.of("alex", 300L, "sam", 300L, "lee", 300L), shares);
        assertEquals(900L, total(shares));
    }

    @Test
    void assignsRemainderCentsInParticipantOrder() {
        Map<String, Long> shares = equalSplit.split(10, List.of("alex", "sam", "lee"));

        assertEquals(List.of("alex", "sam", "lee"), List.copyOf(shares.keySet()));
        assertEquals(List.of(4L, 3L, 3L), List.copyOf(shares.values()));
        assertEquals(10L, total(shares));
    }

    @Test
    void distributesMultipleRemainderCentsToEarliestParticipants() {
        Map<String, Long> shares = equalSplit.split(11, List.of("alex", "sam", "lee", "pat"));

        assertEquals(List.of("alex", "sam", "lee", "pat"), List.copyOf(shares.keySet()));
        assertEquals(List.of(3L, 3L, 3L, 2L), List.copyOf(shares.values()));
        assertEquals(11L, total(shares));
    }

    @Test
    void supportsSingleParticipantAndZeroExpense() {
        assertEquals(Map.of("alex", 725L), equalSplit.split(725, List.of("alex")));
        Map<String, Long> zeroShares = equalSplit.split(0, List.of("alex", "sam"));
        assertEquals(Map.of("alex", 0L, "sam", 0L), zeroShares);
        assertEquals(0L, total(zeroShares));
    }

    @Test
    void handlesMaximumLongExpenseWithoutOverflow() {
        Map<String, Long> shares = equalSplit.split(Long.MAX_VALUE, List.of("alex", "sam", "lee"));

        long baseShare = Long.MAX_VALUE / 3;
        assertEquals(Map.of("alex", baseShare + 1, "sam", baseShare, "lee", baseShare), shares);
        assertEquals(Long.MAX_VALUE, total(shares));
    }

    @Test
    void acceptsCustomSharesThatMatchExpense() {
        Map<String, Long> allocations = linkedAllocations("alex", 525L, "sam", 275L);

        Map<String, Long> shares = customSplit.split(800, allocations);

        assertEquals(allocations, shares);
        assertEquals(List.copyOf(allocations.keySet()), List.copyOf(shares.keySet()));
        assertEquals(800L, total(shares));
    }

    @Test
    void acceptsZeroCustomSharesWhenTotalIsZero() {
        Map<String, Long> shares = customSplit.split(0, linkedAllocations("alex", 0L, "sam", 0L));

        assertEquals(Map.of("alex", 0L, "sam", 0L), shares);
    }

    @Test
    void rejectsNegativeExpenseForBothStrategies() {
        assertThrows(IllegalArgumentException.class, () -> equalSplit.split(-1, List.of("alex")));
        assertThrows(IllegalArgumentException.class,
                () -> customSplit.split(-1, linkedAllocations("alex", 0L)));
    }

    @Test
    void rejectsNullOrEmptyParticipantCollections() {
        assertThrows(IllegalArgumentException.class, () -> equalSplit.split(100, null));
        assertThrows(IllegalArgumentException.class, () -> equalSplit.split(100, List.of()));
        assertThrows(IllegalArgumentException.class, () -> customSplit.split(100, null));
        assertThrows(IllegalArgumentException.class, () -> customSplit.split(0, Map.of()));
    }

    @Test
    void rejectsDuplicateBlankAndNullEqualParticipants() {
        assertThrows(IllegalArgumentException.class, () -> equalSplit.split(100, List.of("alex", "alex")));
        assertThrows(IllegalArgumentException.class, () -> equalSplit.split(100, List.of(" ")));
        assertThrows(IllegalArgumentException.class,
                () -> equalSplit.split(100, java.util.Arrays.asList("alex", null)));
    }

    @Test
    void rejectsCustomAllocationsThatDoNotMatchExpense() {
        assertThrows(IllegalArgumentException.class,
                () -> customSplit.split(100, linkedAllocations("alex", 40L, "sam", 50L)));
        assertThrows(IllegalArgumentException.class,
                () -> customSplit.split(100, linkedAllocations("alex", 110L)));
    }

    @Test
    void rejectsNegativeOrNullCustomAmountsAndInvalidParticipantIds() {
        assertThrows(IllegalArgumentException.class,
                () -> customSplit.split(0, linkedAllocations("alex", -1L, "sam", 1L)));
        Map<String, Long> nullAmount = new LinkedHashMap<>();
        nullAmount.put("alex", null);
        assertThrows(IllegalArgumentException.class, () -> customSplit.split(0, nullAmount));
        assertThrows(IllegalArgumentException.class,
                () -> customSplit.split(0, linkedAllocations(" ", 0L)));
        Map<String, Long> nullId = new LinkedHashMap<>();
        nullId.put(null, 0L);
        assertThrows(IllegalArgumentException.class, () -> customSplit.split(0, nullId));
    }

    @Test
    void rejectsCustomAllocationSumOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> customSplit.split(Long.MAX_VALUE,
                        linkedAllocations("alex", Long.MAX_VALUE, "sam", 1L)));
    }

    @Test
    void returnedSharesCannotBeMutated() {
        Map<String, Long> equalShares = equalSplit.split(2, List.of("alex", "sam"));
        Map<String, Long> customShares = customSplit.split(2, Map.of("alex", 1L, "sam", 1L));

        assertEquals(Map.of("alex", 1L, "sam", 1L), equalShares);
        assertEquals(Map.of("alex", 1L, "sam", 1L), customShares);
        assertThrows(UnsupportedOperationException.class, () -> equalShares.put("lee", 0L));
        assertThrows(UnsupportedOperationException.class, () -> customShares.put("lee", 0L));
    }

    private static long total(Map<String, Long> shares) {
        return shares.values().stream()
                .mapToLong(Long::longValue)
                .reduce(0L, Math::addExact);
    }

    private static Map<String, Long> linkedAllocations(String participant, Long amount) {
        Map<String, Long> allocations = new LinkedHashMap<>();
        allocations.put(participant, amount);
        return allocations;
    }

    private static Map<String, Long> linkedAllocations(
            String firstParticipant, Long firstAmount, String secondParticipant, Long secondAmount) {
        Map<String, Long> allocations = linkedAllocations(firstParticipant, firstAmount);
        allocations.put(secondParticipant, secondAmount);
        return allocations;
    }
}
