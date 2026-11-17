package edu.group17.roommates.household;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class HouseholdService {
    private final HouseholdStore store;
    public HouseholdService(HouseholdStore store) { this.store = store; }

    public Household create(String name, String uid) {
        requireIdentity(uid);
        if (name == null || name.isBlank() || name.length() > 80) {
            throw new IllegalArgumentException("Household name must contain 1 to 80 characters");
        }
        return store.create(name.strip(), uid);
    }

    public List<Household> list(String uid) {
        requireIdentity(uid);
        return store.listForMember(uid);
    }

    private static void requireIdentity(String uid) {
        if (uid == null || uid.isBlank()) throw new IllegalArgumentException("Authenticated UID is required");
    }
}
