package edu.group17.roommates.balance;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import edu.group17.roommates.expense.FirestoreExpenseStore;
import edu.group17.roommates.household.HouseholdAccessGuard;
import edu.group17.roommates.household.HouseholdStorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class SettlementService {
    private final Firestore firestore;
    private final HouseholdAccessGuard access;
    private final FirestoreExpenseStore expenses;
    private final FirestoreSettlementStore settlements;
    private final BalanceCalculator calculator;

    public SettlementService(Firestore firestore, HouseholdAccessGuard access, FirestoreExpenseStore expenses,
                             FirestoreSettlementStore settlements, BalanceCalculator calculator) {
        this.firestore = firestore;
        this.access = access;
        this.expenses = expenses;
        this.settlements = settlements;
        this.calculator = calculator;
    }

    public SettlementReceipt record(String householdId, String callerUid, String toUid, String currency,
                                    long amountCents, String requestId) {
        if (callerUid == null || callerUid.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (householdId == null || !householdId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (callerUid.equals(toUid)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot pay yourself");
        var householdRef = firestore.collection("households").document(householdId);
        var settlementRef = householdRef.collection("settlements").document(requestId);
        return await(firestore.runTransaction(transaction -> {
            var household = access.requireMember(transaction, householdId, callerUid);
            var rawMembers = household.get("memberIds");
            if (!(rawMembers instanceof List<?> values)
                    || values.stream().anyMatch(value -> !(value instanceof String))) {
                throw new IllegalStateException("Stored household members are invalid");
            }
            var memberIds = new ArrayList<String>(values.size());
            for (Object value : values) memberIds.add((String) value);
            if (!memberIds.contains(toUid)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

            var existing = await(transaction.get(settlementRef));
            if (existing.exists()) {
                if (!callerUid.equals(existing.getString("fromUid"))
                        || !toUid.equals(existing.getString("toUid"))
                        || !currency.equals(existing.getString("currency"))
                        || !Long.valueOf(amountCents).equals(existing.getLong("amountCents"))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment request ID already used");
                }
                return new SettlementReceipt(requestId, householdId, callerUid, toUid, currency, amountCents);
            }

            var balance = calculator.calculate(householdId, memberIds,
                    expenses.listAllForBalances(transaction, householdId),
                    settlements.listForHousehold(transaction, householdId));
            var debt = balance.debts().stream()
                    .filter(item -> callerUid.equals(item.debtorId()) && toUid.equals(item.creditorId())
                            && currency.equals(item.currency()))
                    .findFirst().orElse(null);
            if (debt == null || amountCents > debt.amountCents()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Outstanding balance has changed");
            }
            transaction.create(settlementRef, Map.of(
                    "householdId", householdId,
                    "fromUid", callerUid,
                    "toUid", toUid,
                    "currency", currency,
                    "amountCents", amountCents,
                    "createdAt", Timestamp.now()));
            transaction.update(householdRef, "settlementVersion", FieldValue.increment(1));
            return new SettlementReceipt(requestId, householdId, callerUid, toUid, currency, amountCents);
        }));
    }

    public record SettlementReceipt(String id, String householdId, String fromUid, String toUid,
                                    String currency, long amountCents) {}

    private static <T> T await(ApiFuture<T> future) {
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HouseholdStorageException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ResponseStatusException status) throw status;
            }
            throw new HouseholdStorageException(exception);
        }
    }
}
