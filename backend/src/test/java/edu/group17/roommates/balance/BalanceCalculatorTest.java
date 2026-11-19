package edu.group17.roommates.balance;

import edu.group17.roommates.expense.Expense;
import edu.group17.roommates.expense.ExpenseCategory;
import edu.group17.roommates.expense.SplitType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BalanceCalculatorTest {
    private final BalanceCalculator calculator = new BalanceCalculator();

    @Test void aggregatesMultiplePayersAndExcludesEachPayersOwnEqualShare() {
        var first = expense("home", "rent", 300, "USD", "alice", List.of("alice", "bob", "carol"),
                SplitType.EQUAL, Map.of("alice", 100L, "bob", 100L, "carol", 100L));
        var second = expense("home", "groceries", 200, "USD", "bob", List.of("bob", "carol"),
                SplitType.EQUAL, Map.of("bob", 100L, "carol", 100L));

        HouseholdBalance result = calculator.calculate("home", List.of("alice", "bob", "carol"),
                List.of(first, second), List.of());

        assertEquals(List.of(
                new HouseholdBalance.DirectedDebt("bob", "alice", "USD", 100),
                new HouseholdBalance.DirectedDebt("carol", "alice", "USD", 100),
                new HouseholdBalance.DirectedDebt("carol", "bob", "USD", 100)), result.debts());
        assertEquals(300, result.totals().getFirst().totalOwingCents());
        assertEquals(result.totals().getFirst().totalOwingCents(), result.totals().getFirst().totalOwedCents());
        assertEquals(new HouseholdBalance.PersonalTotals(100, 100, 0), member(result, "bob").byCurrency().get("USD"));
        assertEquals(new HouseholdBalance.PersonalTotals(0, 200, 200), member(result, "alice").byCurrency().get("USD"));
    }

    @Test void netsReciprocalCustomShareDebtsToZero() {
        var alicePaid = expense("home", "custom one", 900, "USD", "alice", List.of("alice", "bob"),
                SplitType.CUSTOM, Map.of("alice", 500L, "bob", 400L));
        var bobPaid = expense("home", "custom two", 500, "USD", "bob", List.of("bob", "alice"),
                SplitType.CUSTOM, Map.of("bob", 100L, "alice", 400L));

        HouseholdBalance result = calculator.calculate("home", List.of("alice", "bob"),
                List.of(alicePaid, bobPaid), List.of());

        assertTrue(result.debts().isEmpty());
        assertEquals(new HouseholdBalance.PersonalTotals(0, 0, 0), member(result, "alice").byCurrency().get("USD"));
        assertEquals(0, result.totals().getFirst().totalOwingCents());
    }

    @Test void includesHouseholdMembersWithZeroDebtAndReturnsNoDebtForPayersOwnShare() {
        var expense = expense("home", "solo", 250, "USD", "alice", List.of("alice"),
                SplitType.EQUAL, Map.of("alice", 250L));

        HouseholdBalance result = calculator.calculate("home", List.of("alice", "bob"), List.of(expense), List.of());

        assertTrue(result.debts().isEmpty());
        assertEquals(List.of("alice", "bob"), result.members().stream().map(HouseholdBalance.MemberBalance::memberId).toList());
        assertEquals(new HouseholdBalance.PersonalTotals(0, 0, 0), member(result, "bob").byCurrency().get("USD"));
    }

    @Test void successfulSettlementReducesTheNetDebtWithoutChangingExpenses() {
        var expense = expense("home", "dinner", 1000, "USD", "alice", List.of("alice", "bob"),
                SplitType.CUSTOM, Map.of("alice", 600L, "bob", 400L));

        HouseholdBalance result = calculator.calculate("home", List.of("alice", "bob"), List.of(expense),
                List.of(new Settlement("bob", "alice", "USD", 125)));

        assertEquals(List.of(new HouseholdBalance.DirectedDebt("bob", "alice", "USD", 275)), result.debts());
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate("home", List.of("alice", "bob"),
                List.of(expense), List.of(new Settlement("bob", "alice", "USD", 401))));
    }

    @Test void appliesSettlementsBeforeNettingNewReciprocalExpenseObligations() {
        var aliceOwes = expense("home", "alice paid", 100, "USD", "bob", List.of("bob", "alice"),
                SplitType.EQUAL, Map.of("bob", 50L, "alice", 50L));
        var bobOwesLater = expense("home", "bob paid", 100, "USD", "alice", List.of("alice", "bob"),
                SplitType.EQUAL, Map.of("alice", 50L, "bob", 50L));

        HouseholdBalance result = calculator.calculate("home", List.of("alice", "bob"),
                List.of(aliceOwes, bobOwesLater), List.of(new Settlement("alice", "bob", "USD", 50)));

        assertEquals(List.of(new HouseholdBalance.DirectedDebt("bob", "alice", "USD", 50)), result.debts());
    }

    @Test void calculatesEachCurrencySeparatelyWithoutConvertingCents() {
        var usd = expense("home", "usd", 100, "USD", "alice", List.of("alice", "bob"),
                SplitType.EQUAL, Map.of("alice", 50L, "bob", 50L));
        var eur = expense("home", "eur", 200, "EUR", "bob", List.of("bob", "alice"),
                SplitType.EQUAL, Map.of("bob", 100L, "alice", 100L));

        HouseholdBalance result = calculator.calculate("home", List.of("alice", "bob"), List.of(usd, eur), List.of());

        assertEquals(List.of("EUR", "USD"), result.totals().stream().map(HouseholdBalance.CurrencyTotal::currency).toList());
        assertEquals(50, member(result, "bob").byCurrency().get("USD").owesCents());
        assertEquals(100, member(result, "alice").byCurrency().get("EUR").owesCents());
    }

    @Test void rejectsLongOverflowAndCorruptOrCrossHouseholdExpenseData() {
        var huge = expense("home", "huge", Long.MAX_VALUE, "USD", "alice", List.of("bob"),
                SplitType.CUSTOM, Map.of("bob", Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> calculator.calculate("home", List.of("alice", "bob"),
                List.of(huge, huge), List.of()));

        var otherHouse = expense("other", "other", 100, "USD", "alice", List.of("alice", "bob"),
                SplitType.EQUAL, Map.of("alice", 50L, "bob", 50L));
        assertThrows(IllegalStateException.class, () -> calculator.calculate("home", List.of("alice", "bob"),
                List.of(otherHouse), List.of()));
        var badTotals = expense("home", "bad", 100, "USD", "alice", List.of("alice", "bob"),
                SplitType.CUSTOM, Map.of("alice", 50L, "bob", 49L));
        assertThrows(IllegalStateException.class, () -> calculator.calculate("home", List.of("alice", "bob"),
                List.of(badTotals), List.of()));
    }

    private static HouseholdBalance.MemberBalance member(HouseholdBalance balance, String uid) {
        return balance.members().stream().filter(item -> item.memberId().equals(uid)).findFirst().orElseThrow();
    }

    private static Expense expense(String householdId, String description, long amount, String currency,
                                   String payer, List<String> participants, SplitType splitType,
                                   Map<String, Long> shares) {
        return new Expense(description, householdId, description, amount, currency, payer, participants,
                LocalDate.parse("2026-11-17"), ExpenseCategory.OTHER, splitType, shares, payer,
                Instant.parse("2026-11-18T12:00:00Z"));
    }
}
