package edu.group17.roommates.account;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserProfileStoreTest {
    @Test void usesTheDisplayNameOrEmailUsernameWithoutInventingAnIdentity() {
        assertEquals("Joseph", UserProfileStore.username(" Joseph ", "joseph@example.com"));
        assertEquals("joseph45", UserProfileStore.username(null, "joseph45@example.com"));
        assertEquals("joseph45", UserProfileStore.username(" ", "joseph45@example.com"));
        assertNull(UserProfileStore.username(null, null));
    }
}
