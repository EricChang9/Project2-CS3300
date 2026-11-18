# Invitation UI and backend contract

**Integration dependency: backend PR #49 (issues #9/#10).** This frontend PR
implements issue #12's UI against the contract below, which #49 implements and
tests. Keep the frontend draft until its backend dependency is reviewed and
integrated. See `backend/INVITATIONS.md` after combining the branches for expiry,
reuse, membership authorization, and concurrency behavior. No production code
generates fake codes or silently simulates a successful join.

All calls go through the shared bearer-token client. Codes are trimmed but retain
case. They stay in component memory and are never logged or put into URLs/storage.

## Create a code

`POST /api/households/{encodedHouseholdId}/invitations` with no body returns 201:

```json
{ "code": "unguessable-case-sensitive-code", "expiresAt": "2026-11-20T20:00:00Z" }
```

The backend must authorize membership, generate an unguessable code, persist its
expiry, and define its reuse policy. The UI displays expiry, copies via Clipboard
API, and offers manual selection/copy if permission is denied. It refuses to copy
a code already expired at click time. Changing household unmounts the code view,
aborts the old request, and ignores late responses. A 403 shows a permissions
message; unavailable/failing APIs display an error without inventing a code.

## Redeem a code

`POST /api/invitations/redeem` with `{ "code": "..." }` returns 200:

```json
{ "householdId": "existing-household-id", "status": "joined" }
```

Repeated membership uses `"status": "already_member"` with the same household ID.
The backend must perform atomic membership changes and never trust client identity.
Return 400/404 for invalid codes and 410 for expired codes, without leaking
financial information. The UI also tolerates a 409 already-member response, but
the 200 result above allows selecting the exact household after a refresh.

Successful redemption reloads `GET /api/households` and selects the returned ID.
If only the reload fails, the UI reports the successful join and offers a household
refresh rather than encouraging another redemption. Duplicate pending submits
are blocked. Failed validation preserves the entered code for correction.

## Verification

`npm test` covers sharing/copy, joining from an empty account, invalid/expired
codes, existing membership, pending-submit protection, late results after switching,
clipboard denial, and unavailable APIs. `npm run build` verifies compilation.
Frontend tests mock this API contract. Backend PR #49 adds real emulator-backed
HTTP creation/redemption, membership, expiry, and concurrency tests. No backend
files change in the frontend PR itself.
