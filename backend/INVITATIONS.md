# Household access and invitations

These endpoints require `FIRESTORE_ENABLED=true`, database configuration, and a
verified Firebase ID token. For local setup, start the Firestore emulator and
load `backend/.env` in the backend shell (`set -a`, `source .env`, `set +a`) before
running `./mvnw spring-boot:run`. Spring does not load that shell file itself.

## Creating and redeeming invitations

`POST /api/households/{householdId}/invitations` returns 201 with `{ "code": "...",
"expiresAt": "..." }`. The verified caller must have both a persisted membership
document and an entry in the household's `memberIds` array. Authorization is
checked inside the same Firestore transaction that stores the invitation.

Codes contain 256 bits of cryptographic randomness, encoded as 43 case-sensitive
Base64 URL characters without padding. Only the SHA-256 hash is stored as the
invitation document ID; the plaintext code is returned once for sharing. Never
log codes or put them in URLs. Validation failures return generic messages to
avoid Spring logging rejected code values.

Codes last **48 hours** and may be reused by multiple roommates until expiry.
Creating a new code does not revoke previous unexpired codes. The collection is
`householdInvitations/{codeHash}` with `householdId`, `createdBy`, server
`createdAt`, and `expiresAt`. Expiry is enforced on every redemption; automatic
deletion/TTL and explicit invitation revocation are future work.

`POST /api/invitations/redeem` takes `{ "code": "..." }` and returns 200 with
`{ "householdId": "...", "status": "joined" }` or `"already_member"`.
The authenticated UID is authoritative. A Firestore transaction reads the code,
expiry, household, and existing membership, then atomically writes membership
and updates `memberIds` with `arrayUnion`. Concurrent/repeated redemption of the
same code by the same user creates exactly one membership. A valid code also
repairs inconsistent duplicate membership representations atomically.

Missing/invalid authentication returns 401. Malformed codes return 400; unknown
codes and missing households return 404; expired codes return 410. Missing
households and nonmember invitation creators both return 404 to avoid exposing
household existence. Database failures return a generic 503. Responses expose
no household finances or code contents in error messages.

## Guard for later household APIs

Inject `HouseholdAccessGuard` into services. Use `requireMember(householdId, uid)`
before household-scoped reads. For writes, use its `Transaction` overload inside
the same database transaction so concurrent membership changes cannot race the
write. For independently stored nested resources, call
`requireResource(householdId, uid, serviceConstructedReference)`; this checks
membership before reading the resource and verifies its persisted `householdId`
matches the requested household. Services construct/allowlist collection paths;
never accept arbitrary document paths from clients. Future financial writes
must additionally verify ownership, state, and operation-specific permissions.

## Tests

With the emulator running, from `backend/`:

```sh
FIRESTORE_EMULATOR_HOST=127.0.0.1:8081 ./mvnw verify
```

JUnit and MockMvc tests cover verified identity, anonymous callers, nonmembers,
invalid nested-resource ownership, malformed/unknown/expired codes, successful
HTTP create/redeem/list, existing membership, reuse by multiple users, hashed
storage, and eight concurrent redemptions resulting in one new membership.
Integration fixtures use unique IDs and remove only their own documents.
