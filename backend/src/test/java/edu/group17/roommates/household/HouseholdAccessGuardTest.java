package edu.group17.roommates.household;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HouseholdAccessGuardTest {
    private final Firestore firestore = mock(Firestore.class);
    private final DocumentSnapshot household = mock(DocumentSnapshot.class);
    private final DocumentSnapshot member = mock(DocumentSnapshot.class);
    private final HouseholdAccessGuard guard = new HouseholdAccessGuard(firestore);

    @BeforeEach
    void persistedMembership() {
        var homes = mock(CollectionReference.class); var home = mock(DocumentReference.class);
        var members = mock(CollectionReference.class); var membership = mock(DocumentReference.class);
        when(firestore.collection("households")).thenReturn(homes);
        when(homes.document("home")).thenReturn(home);
        when(home.collection("members")).thenReturn(members);
        when(members.document(FirestoreHouseholdStore.memberDocumentId("alice"))).thenReturn(membership);
        when(home.get()).thenReturn(ApiFutures.immediateFuture(household));
        when(membership.get()).thenReturn(ApiFutures.immediateFuture(member));
        when(household.exists()).thenReturn(true); when(household.get("memberIds")).thenReturn(List.of("alice"));
        when(member.exists()).thenReturn(true); when(member.getString("uid")).thenReturn("alice");
    }

    @Test void acceptsPersistedMembership() { assertSame(household, guard.requireMember("home", "alice")); }

    @Test void deniesMissingOrInconsistentMembershipBeforeReadingNestedData() {
        var resource = mock(DocumentReference.class);
        when(member.exists()).thenReturn(false);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> guard.requireResource("home", "alice", resource)).getStatusCode().value());
        verifyNoInteractions(resource);
        when(member.exists()).thenReturn(true); when(household.get("memberIds")).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> guard.requireMember("home", "alice"));
    }

    @Test void checksNestedResourceHouseholdAfterMembership() {
        var reference = mock(DocumentReference.class); var resource = mock(DocumentSnapshot.class);
        when(reference.get()).thenReturn(ApiFutures.immediateFuture(resource)); when(resource.exists()).thenReturn(true);
        when(resource.getString("householdId")).thenReturn("other-home");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> guard.requireResource("home", "alice", reference)).getStatusCode().value());
        when(resource.getString("householdId")).thenReturn("home");
        assertSame(resource, guard.requireResource("home", "alice", reference));
    }

    @Test void rejectsUntrustedPathsAndMissingIdentity() {
        assertThrows(ResponseStatusException.class, () -> guard.requireMember("../home", "alice"));
        assertThrows(ResponseStatusException.class, () -> guard.requireMember("home", null));
    }
}
