package edu.group17.roommates.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Server-only database access. Services must enforce household authorization. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class FirestoreConfiguration {
    @Bean(destroyMethod = "close")
    Firestore firestore(@Value("${app.firestore.project-id:}") String projectId,
                        @Value("${app.firestore.emulator-host:}") String emulatorHost) {
        return options(projectId, emulatorHost).getService();
    }

    static FirestoreOptions options(String projectId, String emulatorHost) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("GOOGLE_CLOUD_PROJECT is required when Firestore is enabled");
        }
        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder().setProjectId(projectId);
        if (emulatorHost != null && !emulatorHost.isBlank()) {
            if (emulatorHost.contains("://") || emulatorHost.contains("/")) {
                throw new IllegalArgumentException("FIRESTORE_EMULATOR_HOST must be host:port without a scheme");
            }
            builder.setEmulatorHost(emulatorHost).setCredentials(new FirestoreOptions.EmulatorCredentials());
        }
        // Without an emulator, the SDK resolves Application Default Credentials.
        return builder.build();
    }
}
