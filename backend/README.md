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

## Account authentication

Accounts use **Firebase Authentication**. Enable the desired sign-in provider
(for example email/password) in your Firebase project. The frontend will use
Firebase's client SDK to register/sign in and obtain an ID token; Spring does not
store passwords or mint its own sessions. Registration screens are a separate issue.

Export `FIREBASE_AUTH_PROJECT_ID` (defaults to `GOOGLE_CLOUD_PROJECT`) for your
Firebase project. If neither is set, the public shell/health still start, but all
protected operations reject authentication. Database and authentication may be
configured independently. The Firestore emulator does not authenticate users.

Clients send `Authorization: Bearer <Firebase ID token>` on each API request.
Spring verifies the RS256 signature against Google's rotating public keys,
issuer `https://securetoken.google.com/<project>`, exact project audience,
nonempty UID of at most 128 characters, expiry, issue time, and authentication time.
Do not send a Firebase custom token, Google OAuth access token, or Gmail token.

`GET /api/me` returns `{ "uid": "...", "email": "...", "displayName": "..." }`;
optional profile fields may be null. UID always comes from verified `sub`, never
from a request parameter. Invalid/missing/expired tokens return 401. Refresh an
expired ID token using the Firebase client SDK. Signing out clears client auth;
already-issued tokens remain valid until expiry (immediate revocation checking is
not implemented). Never log bearer headers or paste real tokens into issue/PR text.

The API is stateless and accepts no authentication cookies or Basic login. CSRF
is disabled because credentials must be explicitly attached as bearer headers.
Cross-origin API access is not enabled; use the existing Vite `/api` proxy locally
and the same origin in the combined deployment. New browser cookie/session auth
would require revisiting this policy. Unimplemented endpoints remain denied.

Local automated authentication tests sign tokens with ephemeral RSA keys and
replace only the decoder's key source using test-only configuration; production
claim validators and the real Spring Security filter chain still execute:

```sh
./mvnw -Dtest=AuthenticationIntegrationTest,FirebaseJwtConfigurationTest,HealthIntegrationTest test
```

Alternatively in powershell:
```sh
.\mvnw.cmd "-Dtest=AuthenticationIntegrationTest,FirebaseJwtConfigurationTest,HealthIntegrationTest" test
```

Tests require no Firebase account, Google keys/network, or real tokens. Production
does not accept unsigned Auth-emulator tokens or test keys. For manual testing,
use a Firebase development project and obtain a normal ID token from its client
SDK, then call `/api/me` from the same-origin app or a REST client.

References: [Firebase ID token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens#verify_id_tokens_using_a_third-party_jwt_library),
[Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Household creation and listing

Enable Firestore using the configuration above. Household endpoints are registered
only when `FIRESTORE_ENABLED=true`; without it the shell/health/current-user API
still start and household routes are unavailable. Both household operations require
a verified bearer token:

- `POST /api/households` with JSON `{ "name": "Our apartment" }` returns **201**
  with a household object. Names must be nonblank and at most 80 characters before
  trimming surrounding whitespace. IDs, creator identity, and timestamps are assigned
  by the server; supplied identity/ID fields are ignored.
- `GET /api/households` returns an array of the caller's households, newest first,
  with household ID as a stable tie-breaker. A caller with no memberships gets `[]`.
  There is no user-ID parameter that grants access to somebody else's households.

Response example:

```json
{
  "id": "server-generated-id",
  "name": "Our apartment",
  "createdBy": "firebase-uid",
  "createdAt": "2026-11-17T20:00:00Z",
  "members": [{ "uid": "firebase-uid", "joinedAt": "2026-11-17T20:00:00Z" }]
}
```

Storage: `households/{generatedId}` contains `name`, `createdBy`, server
`createdAt`, and a `memberIds` array. `households/{id}/members/{encodedUid}` stores
`uid` and server `joinedAt`; `encodedUid` is unpadded Base64 URL encoding of the
UID's UTF-8 bytes so arbitrary Firebase UIDs cannot introduce path segments.
A single atomic Firestore batch creates the household and creator membership.
The `memberIds` array supports the single-field membership query without a
composite index. Future invitation/removal operations must update both membership
representations atomically. Financial endpoints will additionally need issue #9's
membership/resource guard; this PR exposes only create and current-user list.

Invalid input returns 400; missing/invalid identity returns 401. Database failures
return a generic 503 without database details. A timeout may leave the outcome
unknown, so clients should refresh the household list before retrying creation.
Creation is not an idempotent payment operation. Invitations, member removal,
household detail routes, roles, and frontend screens remain separate issues.

`./mvnw verify` runs household service and secure MockMvc controller tests without
Firebase accounts. To also verify real database reads/writes and failed-batch
atomicity, run from the repository root:

```sh
firebase emulators:exec --only firestore --project demo-roommate-expenses \
  'cd backend && ./mvnw --batch-mode --no-transfer-progress verify'
```

Integration tests use unique IDs, a `demo-` project, emulator credentials, bounded
waits, and cleanup. The failure test precreates a conflicting membership, verifies
that the batch creates no household, and preserves the preexisting document.
