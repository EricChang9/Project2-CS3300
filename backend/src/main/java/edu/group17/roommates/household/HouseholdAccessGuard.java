package edu.group17.roommates.household;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/** Services call this before exposing or mutating household-scoped data. */
@Service
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class HouseholdAccessGuard {
    private final Firestore firestore;
    public HouseholdAccessGuard(Firestore firestore) { this.firestore = firestore; }

    public DocumentSnapshot requireMember(String householdId, String uid) {
        return requireMember(householdId, uid, ref -> await(ref.get()));
    }

    public DocumentSnapshot requireMember(Transaction transaction, String householdId, String uid) {
        return requireMember(householdId, uid, ref -> await(transaction.get(ref)));
    }

    private DocumentSnapshot requireMember(String householdId, String uid,
                                           Function<DocumentReference, DocumentSnapshot> read) {
        if (householdId == null || !householdId.matches("[A-Za-z0-9_-]{1,128}") || uid == null || uid.isBlank()) {
            throw hidden();
        }
        var reference = firestore.collection("households").document(householdId);
        var household = read.apply(reference);
        var membership = read.apply(reference.collection("members")
                .document(FirestoreHouseholdStore.memberDocumentId(uid)));
        if (!household.exists() || !membership.exists() || !uid.equals(membership.getString("uid"))
                || !(household.get("memberIds") instanceof List<?> ids) || !ids.contains(uid)) throw hidden();
        return household;
    }

    /** The resource reference is constructed by the service, never a client-supplied path. */
    public DocumentSnapshot requireResource(String householdId, String uid, DocumentReference resourceReference) {
        requireMember(householdId, uid);
        var resource = await(resourceReference.get());
        if (!resource.exists() || !householdId.equals(resource.getString("householdId"))) throw hidden();
        return resource;
    }

    private static ResponseStatusException hidden() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Household resource not found");
    }

    private static <T> T await(ApiFuture<T> future) {
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new HouseholdStorageException(exception);
        } catch (ExecutionException | TimeoutException exception) { throw new HouseholdStorageException(exception); }
    }
}
