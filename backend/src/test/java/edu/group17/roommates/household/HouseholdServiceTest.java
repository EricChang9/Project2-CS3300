package edu.group17.roommates.household;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HouseholdServiceTest {
    private final HouseholdStore store = mock(HouseholdStore.class);
    private final HouseholdService service = new HouseholdService(store);

    @Test
    void trimsNameAndDelegatesUsingCallerIdentity() {
        var household = new Household("id", "Home", "alice", Instant.EPOCH, List.of());
        when(store.create("Home", "alice")).thenReturn(household);
        assertEquals(household, service.create("  Home  ", "alice"));
        when(store.listForMember("alice")).thenReturn(List.of(household));
        assertEquals(List.of(household), service.list("alice"));
        verify(store).create("Home", "alice");
        verify(store).listForMember("alice");
    }

    @Test
    void invalidNamesOrMissingIdentityNeverReachStorage() {
        for (String name : new String[]{null, "", " ", "x".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> service.create(name, "alice"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.create("Home", null));
        assertThrows(IllegalArgumentException.class, () -> service.list(" "));
        verifyNoInteractions(store);
    }

    @Test
    void propagatesFailureWithoutReturningACompletedHousehold() {
        when(store.create("Home", "alice")).thenThrow(new HouseholdStorageException(new RuntimeException()));
        assertThrows(HouseholdStorageException.class, () -> service.create("Home", "alice"));
    }

    @Test
    void acceptsAnEightyCharacterNameAndTrimsIt() {
        String uid = "alice";
        String trimmedName = "x".repeat(79);
        String rawName = trimmedName + " ";

        Household expected = new Household("id", trimmedName, uid, Instant.EPOCH, List.of());
        when(store.create(trimmedName, uid)).thenReturn(expected);

        Household actual = service.create(rawName, uid);

        assertEquals(expected, actual);
        verify(store).create(trimmedName, uid);
    }

    @Test
    void rejectsAnEightyOneCharacterNameBeforeTrimming() {
        String rawName = "x".repeat(80) + " ";

        assertThrows(IllegalArgumentException.class, () -> service.create(rawName, "alice"));

        verifyNoInteractions(store);
    }
}
