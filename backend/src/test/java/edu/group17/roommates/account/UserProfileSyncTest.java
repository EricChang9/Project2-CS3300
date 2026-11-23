package edu.group17.roommates.account;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import edu.group17.roommates.household.HouseholdStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserProfileSyncTest {
    private static final String UID = "room/mate";
    private static final String PROFILE_ID = "cm9vbS9tYXRl";

    private final Firestore firestore = mock(Firestore.class);
    private final DocumentReference profile = mock(DocumentReference.class);
    private final UserProfileStore store = new UserProfileStore(firestore);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"missing-at-sign", "@example.test"})
    void skipsStorageWhenNeitherClaimCanSupplyAUsername(String email) {
        assertNull(store.sync(UID, " \t ", email));
        verifyNoInteractions(firestore);
    }

    @Test
    void mergesTheTrimmedDisplayNameAndUidAtTheEncodedDocumentId() {
        stubProfileWrite(ApiFutures.immediateFuture(null));

        assertEquals("Avery", store.sync(UID, "  Avery  ", "ignored@example.test"));
        verify(profile).set(Map.of("uid", UID, "username", "Avery"), SetOptions.merge());
    }

    @Test
    void wrapsAFailedFirestoreWriteAndRetainsTheCause() {
        var failure = new IllegalStateException("Write failed");
        stubProfileWrite(ApiFutures.immediateFailedFuture(failure));

        var error = assertThrows(HouseholdStorageException.class,
                () -> store.sync(UID, "Avery", null));

        var writeError = assertInstanceOf(ExecutionException.class, error.getCause());
        assertSame(failure, writeError.getCause());
    }

    @Test
    void wrapsATimedOutFirestoreWrite() throws Exception {
        var timeout = new TimeoutException("Write did not finish");
        ApiFuture<WriteResult> write = mock();
        when(write.get(10, TimeUnit.SECONDS)).thenThrow(timeout);
        stubProfileWrite(write);

        var error = assertThrows(HouseholdStorageException.class,
                () -> store.sync(UID, "Avery", null));

        assertSame(timeout, error.getCause());
    }

    @Test
    void restoresTheInterruptFlagWhenTheWriteIsInterrupted() throws Exception {
        var interruption = new InterruptedException("Write interrupted");
        ApiFuture<WriteResult> write = mock();
        when(write.get(10, TimeUnit.SECONDS)).thenThrow(interruption);
        stubProfileWrite(write);

        try {
            var error = assertThrows(HouseholdStorageException.class,
                    () -> store.sync(UID, "Avery", null));

            assertSame(interruption, error.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void stubProfileWrite(ApiFuture<WriteResult> result) {
        CollectionReference profiles = mock(CollectionReference.class);
        when(firestore.collection("userProfiles")).thenReturn(profiles);
        when(profiles.document(PROFILE_ID)).thenReturn(profile);
        when(profile.set(anyMap(), any(SetOptions.class))).thenReturn(result);
    }
}
