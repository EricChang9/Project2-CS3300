# Account screens

Use Node 22.12+ within Node 22. The Firebase browser SDK is pinned to 12.19.0,
which supports this repository's Node version. The newest SDK requires a newer
Node major; upgrading the app runtime is a separate change.
The lockfile uses patched gRPC/Undici overrides for SDK Node dependencies and
a Vite patch update. The existing Picomatch advisory affecting development
tooling has no published fixed release at this writing; it is not addressed
by downgrading the test runner or bypassing npm's engine checks.

Copy `.env.example` to `.env` and fill in the four public Firebase web-app values
from your project's Firebase settings. Enable Email/Password in Firebase
Authentication and authorize the development/deployment host as appropriate.
These web identifiers are public; never put a service-account key, OAuth secret,
or private credential in `VITE_*` variables. Restart Vite after changing them.

The backend must use the same Firebase project via `FIREBASE_AUTH_PROJECT_ID`.
Run it using the backend README, then run `npm ci && npm run dev` here. Without
web configuration the account form is disabled and health connectivity still
works. Gmail authorization is separate and not configured by these screens.

Firebase manages email/password registration, login, token refresh, and its
default local login persistence. After SDK restoration/sign-in, `/api/me` must
verify identity before protected UI renders. Every application request obtains
the current SDK ID token, attaches a bearer header, and omits cookies. A 401
forces one token refresh/retry; persistent rejection clears the session and
returns to sign-in. Account changes invalidate pending responses. Passwords
and raw provider errors are never persisted by application code or logged.

`npm test` covers registration/confirmation, safe failures, pending submits,
restored login, sign-out, late responses, verification outages, and expired-token
handling with an injected fake identity client. `npm run build` builds the
production bundle. Tests use no real accounts and make no Firebase requests.
Real registration/sign-in requires a configured development Firebase project;
the backend deliberately rejects unsigned Firebase Auth emulator tokens.

This branch adds account UI only. Household and invitation screens are subsequent
PRs. Password reset and social sign-in are outside issue #7.

References: [Firebase password authentication](https://firebase.google.com/docs/auth/web/password-auth),
[session persistence](https://firebase.google.com/docs/auth/web/auth-state-persistence).
