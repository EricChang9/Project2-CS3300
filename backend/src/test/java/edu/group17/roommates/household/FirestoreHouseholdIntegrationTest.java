package edu.group17.roommates.household;

import com.google.cloud.firestore.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreHouseholdIntegrationTest {
    private Firestore firestore;
    private DocumentReference document;
    private FirestoreHouseholdStore store;
    private final String uid = "integration-user/" + UUID.randomUUID();

    @BeforeEach
    void connectToEmulatorOnly() {
        firestore = FirestoreOptions.newBuilder().setProjectId("demo-roommate-expenses")
                .setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(new FirestoreOptions.EmulatorCredentials()).build().getService();
        document = firestore.collection("households").document(UUID.randomUUID().toString());
        store = new FirestoreHouseholdStore(firestore, document::getId);
    }

    @AfterEach
    void cleanUpAndCloseClient() throws Exception {
        if (firestore == null) return;
        try {
            for (var member : document.collection("members").get().get(10, TimeUnit.SECONDS).getDocuments()) {
                member.getReference().delete().get(10, TimeUnit.SECONDS);
            }
            document.delete().get(10, TimeUnit.SECONDS);
        } finally { firestore.close(); }
    }

    @Test
    void persistsCreatorMembershipServerTimestampAndListsOnlyMembers() throws Exception {
        var created = new HouseholdService(store).create("  Home  ", uid);
        var snapshot = document.get().get(10, TimeUnit.SECONDS);
        var membership = document.collection("members").document(FirestoreHouseholdStore.memberDocumentId(uid))
                .get().get(10, TimeUnit.SECONDS);
        assertEquals("Home", snapshot.getString("name"));
        assertEquals(uid, snapshot.getString("createdBy"));
        assertNotNull(snapshot.getTimestamp("createdAt"));
        assertEquals(snapshot.getTimestamp("createdAt"), membership.getTimestamp("joinedAt"));
        assertEquals(uid, membership.getString("uid"));
        assertEquals(List.of(created), store.listForMember(uid));
        assertEquals(List.of(), store.listForMember("outsider-" + UUID.randomUUID()));
        assertEquals(created.createdAt(), store.listForMember(uid).getFirst().members().getFirst().joinedAt());
    }

    @Test
    void failedSecondWriteDoesNotLeavePartialHousehold() throws Exception {
        // An existing membership causes batch.create's second write to fail.
        var member = document.collection("members").document(FirestoreHouseholdStore.memberDocumentId(uid));
        member.create(Map.of("existing", true)).get(10, TimeUnit.SECONDS);
        assertThrows(HouseholdStorageException.class, () -> store.create("Home", uid));
        assertFalse(document.get().get(10, TimeUnit.SECONDS).exists());
        assertEquals(Boolean.TRUE, member.get().get(10, TimeUnit.SECONDS).getBoolean("existing"));
        assertEquals(List.of(), store.listForMember(uid));
    }

    @Test
    void invalidNameCreatesNeitherHouseholdNorMembership() throws Exception {
        var service = new HouseholdService(store);
        assertThrows(IllegalArgumentException.class, () -> service.create(" ", uid));
        assertFalse(document.get().get(10, TimeUnit.SECONDS).exists());
        assertTrue(document.collection("members").get().get(10, TimeUnit.SECONDS).isEmpty());
    }
}
