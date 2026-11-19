package edu.group17.roommates.balance;

import edu.group17.roommates.expense.ExpenseStore;
import edu.group17.roommates.household.HouseholdAccessGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class BalanceService {
    private final ExpenseStore expenses;
    private final SettlementStore settlements;
    private final HouseholdAccessGuard access;
    private final BalanceCalculator calculator;

    public BalanceService(ExpenseStore expenses, SettlementStore settlements, HouseholdAccessGuard access,
                          BalanceCalculator calculator) {
        this.expenses = expenses;
        this.settlements = settlements;
        this.access = access;
        this.calculator = calculator;
    }

    public HouseholdBalance get(String householdId, String callerUid) {
        if (callerUid == null || callerUid.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var household = access.requireMember(householdId, callerUid);
        Object rawMemberIds = household.get("memberIds");
        if (!(rawMemberIds instanceof List<?> values)
                || values.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalStateException("Stored household membership is invalid");
        }
        var memberIds = new ArrayList<String>(values.size());
        for (Object value : values) memberIds.add((String) value);
        try {
            return calculator.calculate(householdId, memberIds,
                    expenses.listAllForBalances(householdId, callerUid),
                    settlements.listForHousehold(householdId, callerUid));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Household balance exceeds the supported integer-cent range", exception);
        }
    }
}
