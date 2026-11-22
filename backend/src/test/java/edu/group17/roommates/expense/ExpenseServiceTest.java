package edu.group17.roommates.expense;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

    @Test void boundsHistoryPageSizeBeforeQueryingStorage() {
        assertThrows(IllegalArgumentException.class, () -> service().list("home", "alice", 0, null));
        assertThrows(IllegalArgumentException.class, () -> service().list("home", "alice", 51, null));
        verifyNoInteractions(store);
    }
}
