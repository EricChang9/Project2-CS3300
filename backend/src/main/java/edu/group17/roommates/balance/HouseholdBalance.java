package edu.group17.roommates.balance;

import java.util.List;
import java.util.Map;

/** Outstanding household debts, grouped by currency, with positive net meaning the member is owed money. */
public record HouseholdBalance(String householdId, List<DirectedDebt> debts,
                               List<MemberBalance> members, List<CurrencyTotal> totals) {
    public HouseholdBalance {
        debts = List.copyOf(debts);
        members = List.copyOf(members);
        totals = List.copyOf(totals);
    }

    /** A net obligation after opposite-direction debts and recorded settlements are applied. */
    public record DirectedDebt(String debtorId, String creditorId, String currency, long amountCents) {}

    /** Member totals are separate for each currency; amounts are always integer minor units. */
    public record MemberBalance(String memberId, Map<String, PersonalTotals> byCurrency) {
        public MemberBalance { byCurrency = Map.copyOf(byCurrency); }
    }

    /** netBalanceCents = owedCents - owesCents; positive means the member should receive money. */
    public record PersonalTotals(long owesCents, long owedCents, long netBalanceCents) {}

    /** Household-wide debit and credit totals must match for each currency. */
    public record CurrencyTotal(String currency, long totalOwingCents, long totalOwedCents) {}
}
