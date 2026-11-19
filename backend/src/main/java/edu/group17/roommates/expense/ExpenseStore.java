package edu.group17.roommates.expense;

public interface ExpenseStore {
    Expense create(Expense expense);
    ExpensePage list(String householdId, String uid, int limit, String cursor);
    Expense find(String householdId, String uid, String expenseId);
}
