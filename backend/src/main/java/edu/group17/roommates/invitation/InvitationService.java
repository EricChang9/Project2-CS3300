package edu.group17.roommates.invitation;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import edu.group17.roommates.household.HouseholdAccessGuard;
import edu.group17.roommates.household.HouseholdStorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@ConditionalOnProperty(name = "app.firestore.enabled", havingValue = "true")
public class InvitationService {
    private final Firestore firestore;
    private final HouseholdAccessGuard access;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public InvitationService(Firestore firestore, HouseholdAccessGuard access) {
        this(firestore, access, Clock.systemUTC());
    }

    InvitationService(Firestore firestore, HouseholdAccessGuard access, Clock clock) {
        this.firestore = firestore; this.access = access; this.clock = clock;
    }

    public CreatedInvitation create(String householdId, String uid) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(Duration.ofHours(48));
        var reference = firestore.collection("householdInvitations").document(codeHash(code));
        return await(firestore.runTransaction(transaction -> {
            // Membership is checked in the same transaction as the invitation write.
            access.requireMember(transaction, householdId, uid);
            transaction.create(reference, Map.of("householdId", householdId, "createdBy", uid,
                    "createdAt", FieldValue.serverTimestamp(), "expiresAt", timestamp(expiresAt)));
            return new CreatedInvitation(code, expiresAt);
        }));
    }

    public RedeemedInvitation redeem(String code, String uid) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}")) throw invalid(HttpStatus.BAD_REQUEST);
        if (uid == null || uid.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var invitationReference = firestore.collection("householdInvitations").document(codeHash(code));
        return await(firestore.runTransaction(transaction -> {
            var invitation = transaction.get(invitationReference).get();
            if (!invitation.exists()) throw invalid(HttpStatus.NOT_FOUND);
            Timestamp expiresAt = invitation.getTimestamp("expiresAt");
            if (expiresAt == null || expiresAt.compareTo(timestamp(clock.instant())) <= 0) {
                throw invalid(HttpStatus.GONE);
            }
            String householdId = invitation.getString("householdId");
            if (householdId == null || !householdId.matches("[A-Za-z0-9_-]{1,128}")) throw invalid(HttpStatus.NOT_FOUND);
            var householdReference = firestore.collection("households").document(householdId);
            var membershipReference = householdReference.collection("members").document(memberId(uid));
            var household = transaction.get(householdReference).get();
            var membership = transaction.get(membershipReference).get();
            if (!household.exists()) throw invalid(HttpStatus.NOT_FOUND);
            if (membership.exists() && uid.equals(membership.getString("uid"))
                    && household.get("memberIds") instanceof List<?> ids && ids.contains(uid)) {
                return new RedeemedInvitation(householdId, "already_member");
            }
            transaction.set(membershipReference, Map.of("uid", uid, "joinedAt", FieldValue.serverTimestamp()));
            transaction.update(householdReference, "memberIds", FieldValue.arrayUnion(uid));
            return new RedeemedInvitation(householdId, "joined");
        }));
    }

    static String codeHash(String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(code.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static String memberId(String uid) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uid.getBytes(StandardCharsets.UTF_8));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private static ResponseStatusException invalid(HttpStatus status) {
        return new ResponseStatusException(status,
                status == HttpStatus.GONE ? "Invitation code has expired" : "Invalid invitation code");
    }

    private static <T> T await(ApiFuture<T> future) {
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new HouseholdStorageException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ResponseStatusException status) throw status;
            }
            throw new HouseholdStorageException(exception);
        }
    }

    public record CreatedInvitation(String code, Instant expiresAt) {}
    public record RedeemedInvitation(String householdId, String status) {}
}
