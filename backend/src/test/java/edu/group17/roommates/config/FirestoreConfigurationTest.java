package edu.group17.roommates.config;

import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirestoreConfigurationTest {
    @Test
    void emulatorUsesExplicitProjectAndNoCredentials() {
        var options = FirestoreConfiguration.options("demo-roommate-expenses", "127.0.0.1:8081");
        assertEquals("demo-roommate-expenses", options.getProjectId());
        assertInstanceOf(FirestoreOptions.EmulatorCredentials.class, options.getCredentials());
        assertEquals("127.0.0.1:8081", options.getEmulatorHost());
    }

    @Test
    void rejectsMissingProjectAndMalformedEmulatorHost() {
        assertThrows(IllegalArgumentException.class, () -> FirestoreConfiguration.options(" ", "localhost:8081"));
        assertThrows(IllegalArgumentException.class,
                () -> FirestoreConfiguration.options("demo-test", "http://localhost:8081"));
    }
}
