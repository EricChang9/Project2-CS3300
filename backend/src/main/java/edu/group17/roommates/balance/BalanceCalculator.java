package edu.group17.roommates.balance;

import edu.group17.roommates.expense.Expense;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/** Pure balance calculation over persisted expense shares and successful settlement records. */
@Component
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public final class BalanceCalculator {
    public HouseholdBalance calculate(String householdId, Collection<String> householdMemberIds,
                                      Collection<Expense> expenses, Collection<Settlement> settlements) {
        var members = new TreeSet<String>(householdMemberIds);
        var currencies = new TreeSet<String>();
        var directed = new HashMap<DebtKey, Long>();

        for (Expense expense : expenses) {
            validateExpense(householdId, members, expense);
            currencies.add(expense.currency());
            for (String participantId : expense.participantIds()) {
                if (!participantId.equals(expense.payerId())) {
                    long share = expense.sharesCents().get(participantId);
                    if (share > 0) add(directed, new DebtKey(participantId, expense.payerId(), expense.currency()), share);
                }
            }
        }

        for (Settlement settlement : settlements) {
            validateSettlement(members, settlement);
            currencies.add(settlement.currency());
            var debtKey = new DebtKey(settlement.fromUid(), settlement.toUid(), settlement.currency());
            Long outstanding = directed.get(debtKey);
            if (outstanding == null || settlement.amountCents() > outstanding) {
                throw new IllegalArgumentException("Settlement exceeds the stored directed obligations");
            }
            long remaining = Math.subtractExact(outstanding, settlement.amountCents());
            if (remaining == 0) directed.remove(debtKey);
            else directed.put(debtKey, remaining);
        }
        var netDebts = netOppositeDirections(directed);

        var debts = netDebts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new HouseholdBalance.DirectedDebt(entry.getKey().debtorId(),
                        entry.getKey().creditorId(), entry.getKey().currency(), entry.getValue()))
                .sorted(Comparator.comparing(HouseholdBalance.DirectedDebt::currency)
                        .thenComparing(HouseholdBalance.DirectedDebt::debtorId)
                        .thenComparing(HouseholdBalance.DirectedDebt::creditorId))
                .toList();

        var totalsByCurrency = new TreeMap<String, MutableCurrencyTotal>();
        currencies.forEach(currency -> totalsByCurrency.put(currency, new MutableCurrencyTotal()));
        var memberTotals = new TreeMap<String, Map<String, MutablePersonalTotals>>();
        for (String memberId : members) {
            var perCurrency = new TreeMap<String, MutablePersonalTotals>();
            currencies.forEach(currency -> perCurrency.put(currency, new MutablePersonalTotals()));
            memberTotals.put(memberId, perCurrency);
        }
        for (var debt : debts) {
            var total = totalsByCurrency.get(debt.currency());
            total.totalOwingCents = Math.addExact(total.totalOwingCents, debt.amountCents());
            total.totalOwedCents = Math.addExact(total.totalOwedCents, debt.amountCents());
            var debtor = memberTotals.get(debt.debtorId()).get(debt.currency());
            debtor.owesCents = Math.addExact(debtor.owesCents, debt.amountCents());
            var creditor = memberTotals.get(debt.creditorId()).get(debt.currency());
            creditor.owedCents = Math.addExact(creditor.owedCents, debt.amountCents());
        }

        var resultMembers = new ArrayList<HouseholdBalance.MemberBalance>(members.size());
        for (var member : memberTotals.entrySet()) {
            var perCurrency = new TreeMap<String, HouseholdBalance.PersonalTotals>();
            for (var balance : member.getValue().entrySet()) {
                var values = balance.getValue();
                perCurrency.put(balance.getKey(), new HouseholdBalance.PersonalTotals(values.owesCents,
                        values.owedCents, Math.subtractExact(values.owedCents, values.owesCents)));
            }
            resultMembers.add(new HouseholdBalance.MemberBalance(member.getKey(), perCurrency));
        }
        var resultTotals = totalsByCurrency.entrySet().stream().map(entry ->
                new HouseholdBalance.CurrencyTotal(entry.getKey(), entry.getValue().totalOwingCents,
                        entry.getValue().totalOwedCents)).toList();
        return new HouseholdBalance(householdId, debts, resultMembers, resultTotals);
    }

    private static void validateExpense(String householdId, Set<String> members, Expense expense) {
        if (expense == null || !householdId.equals(expense.householdId()) || expense.amountCents() <= 0
                || !validCurrency(expense.currency()) || expense.payerId() == null
                || !members.contains(expense.payerId()) || expense.participantIds() == null
                || expense.participantIds().isEmpty() || expense.sharesCents() == null) {
            throw new IllegalStateException("Stored expense fields are invalid for balance calculation");
        }
        var participants = new HashSet<>(expense.participantIds());
        if (participants.size() != expense.participantIds().size() || !members.containsAll(participants)
                || !participants.equals(expense.sharesCents().keySet())) {
            throw new IllegalStateException("Stored expense shares do not match household participants");
        }
        long sharesTotal = 0;
        for (long share : expense.sharesCents().values()) {
            if (share < 0) throw new IllegalStateException("Stored expense shares cannot be negative");
            sharesTotal = Math.addExact(sharesTotal, share);
        }
        if (sharesTotal != expense.amountCents()) {
            throw new IllegalStateException("Stored expense shares do not equal the expense amount");
        }
    }

    private static void validateSettlement(Set<String> members, Settlement settlement) {
        if (settlement == null || settlement.fromUid() == null || settlement.toUid() == null
                || settlement.fromUid().equals(settlement.toUid()) || !members.contains(settlement.fromUid())
                || !members.contains(settlement.toUid()) || !validCurrency(settlement.currency())
                || settlement.amountCents() <= 0) {
            throw new IllegalStateException("Stored settlement fields are invalid");
        }
    }

    private static boolean validCurrency(String currency) {
        if (currency == null) return false;
        try { return Currency.getInstance(currency).getDefaultFractionDigits() == 2; }
        catch (IllegalArgumentException exception) { return false; }
    }

    private static Map<DebtKey, Long> netOppositeDirections(Map<DebtKey, Long> debts) {
        var pairs = new TreeSet<MemberPair>(Comparator.comparing(MemberPair::first).thenComparing(MemberPair::second)
                .thenComparing(MemberPair::currency));
        debts.keySet().forEach(key -> pairs.add(MemberPair.of(key.debtorId(), key.creditorId(), key.currency())));
        var result = new HashMap<DebtKey, Long>();
        for (var pair : pairs) {
            var firstToSecond = new DebtKey(pair.first(), pair.second(), pair.currency());
            var secondToFirst = new DebtKey(pair.second(), pair.first(), pair.currency());
            long firstAmount = debts.getOrDefault(firstToSecond, 0L);
            long secondAmount = debts.getOrDefault(secondToFirst, 0L);
            if (firstAmount > secondAmount) result.put(firstToSecond, Math.subtractExact(firstAmount, secondAmount));
            else if (secondAmount > firstAmount) result.put(secondToFirst, Math.subtractExact(secondAmount, firstAmount));
        }
        return result;
    }

    private static void add(Map<DebtKey, Long> debts, DebtKey key, long amount) {
        debts.put(key, Math.addExact(debts.getOrDefault(key, 0L), amount));
    }

    private record DebtKey(String debtorId, String creditorId, String currency) {}
    private record MemberPair(String first, String second, String currency) {
        static MemberPair of(String a, String b, String currency) {
            return a.compareTo(b) <= 0 ? new MemberPair(a, b, currency) : new MemberPair(b, a, currency);
        }
    }
    private static final class MutablePersonalTotals { long owesCents; long owedCents; }
    private static final class MutableCurrencyTotal { long totalOwingCents; long totalOwedCents; }
}
