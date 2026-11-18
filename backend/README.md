# Backend integration setup

The public shell and health endpoint run without database credentials. Enable
server-side Firestore access explicitly when implementing or running data APIs:

```sh
export FIRESTORE_ENABLED=true
export GOOGLE_CLOUD_PROJECT=demo-roommate-expenses
export FIRESTORE_EMULATOR_HOST=127.0.0.1:8081
```

Alternatively in powershell:
```sh
$env:FIRESTORE_ENABLED = "true"
$env:GOOGLE_CLOUD_PROJECT = "demo-roommate-expenses"
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8081"
```

Start the emulator from the repository root (Java 21 and Firebase CLI required):

```sh
firebase emulators:start --only firestore --project demo-roommate-expenses
```

In another terminal, run the opt-in integration test from `backend/`:

```sh
FIRESTORE_EMULATOR_HOST=127.0.0.1:8081 ./mvnw -Dtest=FirestoreIntegrationTest test
```

Alternatively in powershell:
```sh
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8081"
.\mvnw.cmd -Dtest=FirestoreIntegrationTest test
```

This test uses a fixed `demo-` project, unique document IDs, bounded waits,
cleanup, and a closed client. It never uses production credentials. Without the
emulator variable it is skipped; `./mvnw verify` still runs the unit tests.

Production: set `FIRESTORE_ENABLED=true` and `GOOGLE_CLOUD_PROJECT`, unset
`FIRESTORE_EMULATOR_HOST`, and provide Application Default Credentials through
the runtime service account. Do not commit credential files. The Spring-managed
client closes on application shutdown. Blank project IDs fail startup when enabled.

The server SDK bypasses `firestore.rules`. Keep direct browser database access
denied. Every future repository operation must be called through a service that
checks authenticated identity and household membership; the client does not
provide application authorization. This PR adds no domain repository or endpoint.

Reference: [Firestore emulator setup](https://docs.cloud.google.com/firestore/native/docs/emulator).
