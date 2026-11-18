package edu.group17.roommates.household;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
}
