package edu.group17.roommates.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: never connects to a production database or ADC. */
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreIntegrationTest {
    @Test
    void writesReadsAndDeletesIsolatedDocument() throws Exception {
        try (var firestore = FirestoreConfiguration.options("demo-roommate-expenses",
                System.getenv("FIRESTORE_EMULATOR_HOST")).getService()) {
            var document = firestore.collection("integration-tests").document(UUID.randomUUID().toString());
            try {
                document.set(Map.of("message", "emulator only")).get(10, TimeUnit.SECONDS);
                assertEquals("emulator only", document.get().get(10, TimeUnit.SECONDS).getString("message"));
                document.delete().get(10, TimeUnit.SECONDS);
                assertFalse(document.get().get(10, TimeUnit.SECONDS).exists());
            } finally {
                document.delete().get(10, TimeUnit.SECONDS);
            }
        }
    }
}
