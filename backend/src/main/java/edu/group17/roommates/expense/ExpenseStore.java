package edu.group17.roommates.expense;

import java.util.List;

public interface ExpenseStore {
    Expense create(Expense expense);
    ExpensePage list(String householdId, String uid, int limit, String cursor);
    Expense find(String householdId, String uid, String expenseId);
    /** Loads the authoritative expense and share records used to calculate household balances. */
    List<Expense> listAllForBalances(String householdId, String uid);
}
