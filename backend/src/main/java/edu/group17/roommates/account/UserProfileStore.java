package edu.group17.roommates.account;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import edu.group17.roommates.household.HouseholdStorageException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.*;

/** Only verified identity claims supply profile names; clients cannot choose another UID. */
@Service
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class UserProfileStore {
    private final Firestore firestore;
    public UserProfileStore(Firestore firestore) { this.firestore = firestore; }

    public String sync(String uid, String displayName, String email) {
        String username = username(displayName, email);
        if (username == null) return null;
        try {
            firestore.collection("userProfiles").document(documentId(uid))
                    .set(Map.of("uid", uid, "username", username), SetOptions.merge())
                    .get(10, TimeUnit.SECONDS);
            return username;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new HouseholdStorageException(exception);
        } catch (ExecutionException | TimeoutException exception) { throw new HouseholdStorageException(exception); }
    }

    public static String username(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) return displayName.strip();
        if (email != null && email.indexOf('@') > 0) return email.substring(0, email.indexOf('@'));
        return null;
    }

    public static String documentId(String uid) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uid.getBytes(StandardCharsets.UTF_8));
    }
}
