package edu.group17.roommates.expense;

import java.util.List;

public record ExpensePage(List<Expense> items, String nextCursor) {
    public ExpensePage {
        items = List.copyOf(items);
    }
}
