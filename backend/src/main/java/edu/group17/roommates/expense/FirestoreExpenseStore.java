package edu.group17.roommates.expense;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import edu.group17.roommates.household.HouseholdAccessGuard;
import edu.group17.roommates.household.HouseholdStorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Writes an expense and its per-person shares atomically beneath its household. */
@Repository
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class FirestoreExpenseStore implements ExpenseStore {
    private final Firestore firestore;
    private final HouseholdAccessGuard access;

    public FirestoreExpenseStore(Firestore firestore, HouseholdAccessGuard access) {
        this.firestore = firestore;
        this.access = access;
    }

    @Override
    public Expense create(Expense expense) {
        var household = firestore.collection("households").document(expense.householdId());
        var reference = household.collection("expenses").document(expense.id());
        return await(firestore.runTransaction(transaction -> {
            // Check the authenticated creator, payer, and every share recipient in the same
            // transaction as the writes, so an authorization failure leaves no partial data.
            var users = new LinkedHashSet<String>();
            users.add(expense.createdBy());
            users.add(expense.payerId());
            users.addAll(expense.participantIds());
            for (String uid : users) access.requireMember(transaction, expense.householdId(), uid);

            transaction.create(reference, Map.ofEntries(
                    Map.entry("householdId", expense.householdId()),
                    Map.entry("description", expense.description()),
                    Map.entry("amountCents", expense.amountCents()),
                    Map.entry("currency", expense.currency()),
                    Map.entry("payerId", expense.payerId()),
                    Map.entry("participantIds", expense.participantIds()),
                    Map.entry("expenseDate", expense.expenseDate().toString()),
                    Map.entry("category", expense.category().name()),
                    Map.entry("splitType", expense.splitType().name()),
                    Map.entry("createdBy", expense.createdBy()),
                    Map.entry("createdAt", Timestamp.ofTimeSecondsAndNanos(
                            expense.createdAt().getEpochSecond(), expense.createdAt().getNano()))));
            for (var share : expense.sharesCents().entrySet()) {
                transaction.create(reference.collection("shares").document(memberDocumentId(share.getKey())),
                        Map.of("participantId", share.getKey(), "amountCents", share.getValue()));
            }
            return expense;
        }));
    }

    static String memberDocumentId(String uid) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

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
