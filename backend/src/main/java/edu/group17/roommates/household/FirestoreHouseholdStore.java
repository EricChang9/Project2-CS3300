package edu.group17.roommates.household;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import edu.group17.roommates.account.UserProfileStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Repository
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class FirestoreHouseholdStore implements HouseholdStore {
    private final Firestore firestore;
    private final Supplier<String> householdIds;

    @Autowired
    public FirestoreHouseholdStore(Firestore firestore) {
        this(firestore, () -> firestore.collection("households").document().getId());
    }

    FirestoreHouseholdStore(Firestore firestore, Supplier<String> householdIds) {
        this.firestore = firestore;
        this.householdIds = householdIds;
    }

    @Override
    public Household create(String name, String creatorUid) {
        var household = firestore.collection("households").document(householdIds.get());
        var member = household.collection("members").document(memberDocumentId(creatorUid));
        var batch = firestore.batch();
        batch.create(household, Map.of("name", name, "createdBy", creatorUid,
                "createdAt", FieldValue.serverTimestamp(), "memberIds", List.of(creatorUid)));
        batch.create(member, Map.of("uid", creatorUid, "joinedAt", FieldValue.serverTimestamp()));
        // Firestore commits every write or none; create() never overwrites an existing record.
        await(batch.commit());
        // Read the actual serverTimestamp transform, which need not equal updateTime.
        Instant committedAt = instant(await(household.get()).getTimestamp("createdAt"));
        return new Household(household.getId(), name, creatorUid, committedAt,
                List.of(new Household.Member(creatorUid, committedAt, username(creatorUid))));
    }

    @Override
    public List<Household> listForMember(String uid) {
        var snapshots = await(firestore.collection("households").whereArrayContains("memberIds", uid).get());
        List<Household> households = new ArrayList<>();
        for (var document : snapshots.getDocuments()) {
            var members = await(document.getReference().collection("members").get()).getDocuments().stream()
                    .map(member -> new Household.Member(member.getString("uid"), instant(member.getTimestamp("joinedAt")),
                            username(member.getString("uid"))))
                    .sorted(Comparator.comparing(Household.Member::uid)).toList();
            households.add(new Household(document.getId(), document.getString("name"),
                    document.getString("createdBy"), instant(document.getTimestamp("createdAt")), members));
        }
        households.sort(Comparator.comparing(Household::createdAt).reversed().thenComparing(Household::id));
        return List.copyOf(households);
    }

    // Firebase UIDs may contain characters that are not safe Firestore document paths.
    static String memberDocumentId(String uid) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uid.getBytes(StandardCharsets.UTF_8));
    }

    private String username(String uid) {
        var profile = await(firestore.collection("userProfiles").document(UserProfileStore.documentId(uid)).get());
        return profile.exists() ? profile.getString("username") : null;
    }

    private static Instant instant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static <T> T await(ApiFuture<T> future) {
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HouseholdStorageException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new HouseholdStorageException(e);
        }
    }
}
