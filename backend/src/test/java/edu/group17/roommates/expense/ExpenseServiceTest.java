package edu.group17.roommates.expense;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {
    @Mock ExpenseStore store;

    private ExpenseService service() {
        return new ExpenseService(store, Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test void calculatesAndPersistsEqualSharesWithoutUsingCallerSuppliedShares() {
        var request = new ExpenseService.CreateExpense("Power", 101L, "USD", "alice",
                List.of("alice", "bob"), LocalDate.parse("2026-11-17"), ExpenseCategory.UTILITIES,
                SplitType.EQUAL, null);
        when(store.create(any())).thenAnswer(call -> call.getArgument(0));

        Expense expense = service().create("home", "alice", request);

        assertEquals(101, expense.sharesCents().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(Map.of("alice", 51L, "bob", 50L), expense.sharesCents());
        assertEquals("alice", expense.createdBy());
        assertEquals("home", expense.householdId());
        verify(store).create(expense);
    }

    @Test void calculatesCustomSharesOnlyWhenTheyMatchParticipantsAndTotal() {
        var valid = new ExpenseService.CreateExpense("Groceries", 900L, "USD", "alice",
                List.of("alice", "bob"), LocalDate.parse("2026-11-17"), ExpenseCategory.GROCERIES,
                SplitType.CUSTOM, Map.of("alice", 500L, "bob", 400L));
        when(store.create(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(Map.of("alice", 500L, "bob", 400L), service().create("home", "alice", valid).sharesCents());

        var mismatch = new ExpenseService.CreateExpense("Groceries", 900L, "USD", "alice",
                List.of("alice", "bob"), LocalDate.parse("2026-11-17"), ExpenseCategory.GROCERIES,
                SplitType.CUSTOM, Map.of("alice", 500L, "bob", 399L));
        assertThrows(IllegalArgumentException.class, () -> service().create("home", "alice", mismatch));
        verify(store, times(1)).create(any());
    }

    @Test void rejectsInvalidAmountsCurrencyAndDuplicateParticipantsBeforeStorage() {
        var zero = new ExpenseService.CreateExpense("Rent", 0L, "USD", "alice", List.of("alice"),
                LocalDate.parse("2026-11-17"), ExpenseCategory.HOUSING, SplitType.EQUAL, null);
        var noCents = new ExpenseService.CreateExpense("Rent", 100L, "JPY", "alice", List.of("alice"),
                LocalDate.parse("2026-11-17"), ExpenseCategory.HOUSING, SplitType.EQUAL, null);
        var badCurrency = new ExpenseService.CreateExpense("Rent", 100L, "usd", "alice", List.of("alice"),
                LocalDate.parse("2026-11-17"), ExpenseCategory.HOUSING, SplitType.EQUAL, null);
        var duplicate = new ExpenseService.CreateExpense("Rent", 100L, "USD", "alice", List.of("alice", "alice"),
                LocalDate.parse("2026-11-17"), ExpenseCategory.HOUSING, SplitType.EQUAL, null);

        assertThrows(IllegalArgumentException.class, () -> service().create("home", "alice", zero));
        assertThrows(IllegalArgumentException.class, () -> service().create("home", "alice", noCents));
        assertThrows(IllegalArgumentException.class, () -> service().create("home", "alice", badCurrency));
        assertThrows(IllegalArgumentException.class, () -> service().create("home", "alice", duplicate));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service().create("home", " ", zero));
        verifyNoInteractions(store);
    }

    @Test void summarizesCategoriesByMonthWithoutMixingCurrencies() {
        when(store.listForSummary("home", "alice", YearMonth.parse("2026-11"))).thenReturn(List.of(
                new ExpenseSummaryItem(1000L, "USD", ExpenseCategory.GROCERIES),
                new ExpenseSummaryItem(250L, "USD", ExpenseCategory.GROCERIES),
                new ExpenseSummaryItem(750L, "USD", ExpenseCategory.UTILITIES),
                new ExpenseSummaryItem(500L, "EUR", ExpenseCategory.HOUSING)));

        var summary = service().summary("home", "alice", "2026-11");

        assertEquals("2026-11", summary.month());
        assertEquals(List.of("EUR", "USD"), summary.currencies().stream()
                .map(MonthlySpending.CurrencyTotal::currency).toList());
        var eur = summary.currencies().get(0);
        assertEquals(500L, eur.totalCents());
        assertEquals(1, eur.expenseCount());
        assertEquals(List.of(new MonthlySpending.CategoryTotal(ExpenseCategory.HOUSING, 500L)), eur.categories());
        var usd = summary.currencies().get(1);
        assertEquals(2000L, usd.totalCents());
        assertEquals(3, usd.expenseCount());
        assertEquals(List.of(new MonthlySpending.CategoryTotal(ExpenseCategory.UTILITIES, 750L),
                new MonthlySpending.CategoryTotal(ExpenseCategory.GROCERIES, 1250L)), usd.categories());
    }

    @Test void rejectsInvalidSummaryMonthsBeforeReadingStorage() {
        for (String month : List.of("2026-1", "2026-00", "2026-13", "invalid")) {
            var failure = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> service().summary("home", "alice", month));
            assertEquals(400, failure.getStatusCode().value());
        }
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service().summary("home", " ", "2026-11"));
        verifyNoInteractions(store);
    }

    @Test void handlesEmptyMonthlySummary() {
        when(store.listForSummary("home", "alice", YearMonth.parse("2026-10"))).thenReturn(List.of());
        assertEquals(List.of(), service().summary("home", "alice", "2026-10").currencies());
    }

    @Test void boundsHistoryPageSizeBeforeQueryingStorage() {
        assertThrows(IllegalArgumentException.class, () -> service().list("home", "alice", 0, null));
        assertThrows(IllegalArgumentException.class, () -> service().list("home", "alice", 51, null));
        verifyNoInteractions(store);
    }
}
