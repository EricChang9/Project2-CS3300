# Household workspace

Requires the account screens and backend household API from PR #45. Configure
Firebase web auth as described in `ACCOUNTS.md`. Enable the backend Firestore
client and start the local database emulator using `backend/README.md`.

After `/api/me` verifies login, the workspace calls `GET /api/households` using
the shared bearer-token API client. Creating a home sends `POST /api/households`
with only `{ "name": "..." }`. Server-generated IDs, creator identity, members,
and timestamps remain authoritative. The backend's response contracts are in its README.

The shared `HouseholdProvider` exposes households, selection, selected household,
creation, and refresh. It is keyed by verified UID so another login never inherits
the previous user's selection/data. Selection is in memory, never treated as an
authorization grant. Selected member panels remount when the household ID changes;
future expense/request panels must do the same or cancel obsolete requests.
Aborted/out-of-order list responses cannot replace newer results or selections.

Successful creation is shown even if the following list refresh fails, so the UI
does not suggest duplicating a household. Initial loading, no-household, errors,
retry, and membership display are covered by Vitest/React Testing Library tests.
No household data is invented when the API is unavailable. This PR excludes
invitations and financial screens.
