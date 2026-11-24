package edu.group17.roommates.expense;

import java.time.YearMonth;
import java.util.List;

public interface ExpenseStore {
    Expense create(Expense expense);
    ExpensePage list(String householdId, String uid, int limit, String cursor);
    Expense find(String householdId, String uid, String expenseId);
    List<ExpenseSummaryItem> listForSummary(String householdId, String uid, YearMonth month);
    /** Loads the authoritative expense and share records used to calculate household balances. */
    List<Expense> listAllForBalances(String householdId, String uid);
}
