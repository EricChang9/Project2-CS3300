package edu.group17.roommates.balance;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import edu.group17.roommates.household.HouseholdAccessGuard;
import edu.group17.roommates.household.HouseholdStorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads successful payment records from households/{householdId}/settlements.
 * Future payment writes should store fromUid, toUid, currency, amountCents, and householdId;
 * expense documents and their authoritative shares remain unchanged.
 */
@Repository
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class FirestoreSettlementStore implements SettlementStore {
    private final Firestore firestore;
    private final HouseholdAccessGuard access;

    public FirestoreSettlementStore(Firestore firestore, HouseholdAccessGuard access) {
        this.firestore = firestore;
        this.access = access;
    }

    @Override
    public List<Settlement> listForHousehold(String householdId, String uid) {
        access.requireMember(householdId, uid);
        var documents = await(firestore.collection("households").document(householdId)
                .collection("settlements").get()).getDocuments();
        var settlements = new ArrayList<Settlement>(documents.size());
        for (DocumentSnapshot document : documents) {
            String storedHouseholdId = document.getString("householdId");
            Long amountCents = document.getLong("amountCents");
            String fromUid = document.getString("fromUid");
            String toUid = document.getString("toUid");
            String currency = document.getString("currency");
            if (!householdId.equals(storedHouseholdId) || amountCents == null
                    || fromUid == null || toUid == null || currency == null) {
                throw new IllegalStateException("Stored settlement fields are invalid");
            }
            settlements.add(new Settlement(fromUid, toUid, currency, amountCents));
        }
        return List.copyOf(settlements);
    }

    private static <T> T await(ApiFuture<T> future) {
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HouseholdStorageException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new HouseholdStorageException(exception);
        }
    }
}
