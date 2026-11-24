package edu.group17.roommates.expense;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import edu.group17.roommates.household.HouseholdAccessGuard;
import edu.group17.roommates.household.HouseholdStorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

    @Override
    public ExpensePage list(String householdId, String uid, int limit, String cursorValue) {
        access.requireMember(householdId, uid);
        ExpenseCursor cursor = ExpenseCursor.decode(cursorValue, householdId);
        Query query = firestore.collection("households").document(householdId).collection("expenses")
                .orderBy("expenseDate", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .limit(limit + 1);
        if (cursor != null) query = query.startAfter(cursor.expenseDate().toString(), cursor.expenseId());

        var documents = await(query.get()).getDocuments();
        boolean hasNext = documents.size() > limit;
        int itemCount = Math.min(documents.size(), limit);
        var items = new ArrayList<Expense>(itemCount);
        for (int index = 0; index < itemCount; index++) {
            var document = documents.get(index);
            if (!householdId.equals(document.getString("householdId"))) throw hidden();
            items.add(toExpense(document));
        }
        String nextCursor = hasNext
                ? new ExpenseCursor(householdId, items.getLast().expenseDate(), items.getLast().id()).encode()
                : null;
        return new ExpensePage(items, nextCursor);
    }

    @Override
    public Expense find(String householdId, String uid, String expenseId) {
        if (expenseId == null || !expenseId.matches("[A-Za-z0-9_-]{1,128}")) throw hidden();
        var reference = firestore.collection("households").document(householdId)
                .collection("expenses").document(expenseId);
        var document = access.requireResource(householdId, uid, reference);
        return toExpense(document);
    }

    @Override
    public List<Expense> listAllForBalances(String householdId, String uid) {
        access.requireMember(householdId, uid);
        var documents = await(firestore.collection("households").document(householdId)
                .collection("expenses").get()).getDocuments();
        var expenses = new ArrayList<Expense>(documents.size());
        for (var document : documents) {
            if (!householdId.equals(document.getString("householdId"))) throw hidden();
            expenses.add(toExpense(document));
        }
        return List.copyOf(expenses);
    }

    public List<Expense> listAllForBalances(Transaction transaction, String householdId) {
        var documents = await(transaction.get(firestore.collection("households").document(householdId)
                .collection("expenses"))).getDocuments();
        var expenses = new ArrayList<Expense>(documents.size());
        for (var document : documents) {
            if (!householdId.equals(document.getString("householdId"))) throw hidden();
            expenses.add(toExpense(document, transaction));
        }
        return List.copyOf(expenses);
    }

    private Expense toExpense(DocumentSnapshot document) { return toExpense(document, null); }

    private Expense toExpense(DocumentSnapshot document, Transaction transaction) {
        String householdId = document.getString("householdId");
        String expenseId = document.getId();
        var shares = new LinkedHashMap<String, Long>();
        var sharesQuery = document.getReference().collection("shares");
        var shareDocuments = await(transaction == null ? sharesQuery.get() : transaction.get(sharesQuery))
                .getDocuments();
        for (var share : shareDocuments) {
            String participantId = share.getString("participantId");
            Long amountCents = share.getLong("amountCents");
            if (participantId != null && amountCents != null) shares.put(participantId, amountCents);
        }
        Object rawParticipants = document.get("participantIds");
        if (!(rawParticipants instanceof List<?> participantValues)
                || participantValues.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalStateException("Stored expense participants are invalid");
        }
        @SuppressWarnings("unchecked")
        List<String> participantIds = (List<String>) participantValues;
        Timestamp createdAt = document.getTimestamp("createdAt");
        if (createdAt == null) throw new IllegalStateException("Stored expense timestamp is missing");
        return new Expense(expenseId, householdId, document.getString("description"),
                document.getLong("amountCents"), document.getString("currency"), document.getString("payerId"),
                participantIds,
                java.time.LocalDate.parse(document.getString("expenseDate")),
                ExpenseCategory.valueOf(document.getString("category")), SplitType.valueOf(document.getString("splitType")),
                shares, document.getString("createdBy"), java.time.Instant.ofEpochSecond(
                        createdAt.getSeconds(), createdAt.getNanos()));
    }

    static String memberDocumentId(String uid) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ResponseStatusException hidden() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Household resource not found");
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
