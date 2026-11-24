package edu.group17.roommates.expense;

import java.util.List;

public record MonthlySpending(String month, List<CurrencyTotal> currencies) {
    public record CurrencyTotal(String currency, long totalCents, int expenseCount,
                                List<CategoryTotal> categories) {}
    public record CategoryTotal(ExpenseCategory category, long totalCents) {}
}
