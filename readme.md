# Roommate Expense Sharing — Group 17

CS3300 Project 2: a shared space for roommates to organize bills, track balances,
request payments, and discuss charges without separate spreadsheets and group chats.

## Current status

This repository is a development scaffold, not a completed expense-sharing app.
It includes a React welcome page, a Spring Boot `GET /api/health` endpoint,
a development API proxy, Spring Security defaults, test scaffolding, a Docker
build, Firebase emulator configuration, and GitHub Actions builds and tests.
Accounts, persistence, expense splitting, payment requests, payments, email,
messaging, and summaries are **planned**, not implemented yet.

## Stack

| Area | Technology |
| --- | --- |
| Frontend | React 19, JavaScript, HTML, CSS, Vite |
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, Maven wrapper |
| Database (planned) | Firebase Cloud Firestore |
| Deployment target | GCP Cloud Run; one image serving React and the API |
| Email (planned) | Gmail REST API with OAuth 2.0 through an Adapter |
| Payments (planned) | Local mock supporting success and failure; no Stripe API or real money |
| Tests | JUnit, Spring MockMvc, Vitest, React Testing Library, Playwright |
| Collaboration | GitHub issues, pull requests, GitHub Actions |

We interpret “Firebase database” as **Cloud Firestore** for this scaffold.
Firestore is not connected to Spring Boot yet. No cloud account, credentials,
Gmail authorization, or Stripe account is needed to run the starter.

## Prerequisites

- Node.js **22.12 or newer within Node 22** and npm; `.nvmrc` selects Node 22.
- JDK **21**. Ensure `JAVA_HOME` points to this JDK, including when Maven runs.
- Internet access on first installation to download dependencies and Maven.
- Optional: Docker for the combined image; Firebase CLI for the emulator.

Vite documents its [Node requirements](https://vite.dev/guide/), and Spring
publishes [Java/Maven requirements](https://docs.spring.io/spring-boot/system-requirements.html).

Check your tools:

```sh
node --version
npm --version
java -version
```

On macOS, select the installed JDK 21 in every terminal used for the backend:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

On Windows, set `JAVA_HOME` to your JDK 21 installation and use `mvnw.cmd`
instead of `./mvnw`. The wrapper downloads Maven 3.9.16, so a global Maven
installation is not required.

## Select Node 22 before installing

If you use nvm, run these commands from the repository root in each new terminal:

```sh
nvm install 22
nvm use
node --version
```

If you'd like to install nvm:
On windows: (restart VSCode after installing):
```sh
winget install -e --id CoreyButler.NVMforWindows
```
On macOS/Linux (restart VSCode after installing):
```sh
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.41.0/install.sh | bash
```

If nvm is unavailable, install Node 22 using your preferred installer or version
manager and verify that `node --version` reports v22.12.0 or newer within v22.
A version file does not switch Node automatically unless your shell is configured
for that. Your terminal and IDE may select different Node installations.

## Run locally

Terminal 1 — backend, from the repository root:

```sh
cd backend
./mvnw spring-boot:run
```

Terminal 2 — frontend, from the repository root:

```sh
cd frontend
cp .env.example .env
npm ci
npm run dev
```

Open **http://127.0.0.1:5173**. The page should display **Backend connected**.
Vite proxies `/api` to **http://127.0.0.1:8080**, avoiding cross-origin setup.
You can also verify the backend directly:

```sh
curl --fail http://127.0.0.1:8080/api/health
```

Expected JSON (field order may differ):

```json
{"status":"UP","service":"roommate-expenses"}
```

This endpoint checks application availability only, not database or Gmail connectivity.
The development servers run separately. Building the Docker image combines them.

### Local configuration

| Setting | Default | Effect |
| --- | --- | --- |
| `PORT` | `8080` | Spring Boot port; also used by Cloud Run |
| `API_PROXY_TARGET` | `http://127.0.0.1:8080` | Vite dev proxy destination, in `frontend/.env` |
| `GOOGLE_CLOUD_PROJECT` | Not read yet | Reserved for future Firestore integration |
| `FIRESTORE_EMULATOR_HOST` | Not read yet | Reserved for future Firestore integration |

Spring Boot does **not** automatically load `backend/.env`. The example there
is a reference for future integrations; export backend settings in your shell.
If you change `PORT`, update `API_PROXY_TARGET` to match and restart Vite.
Never put secrets in a `VITE_*` variable: those values are exposed to browsers.

## Tests and builds

Backend (JUnit and MockMvc integration tests):

```sh
cd backend
./mvnw verify
```

Frontend (Vitest and React Testing Library):

```sh
cd frontend
npm ci
npm test
npm run build
```

`npm run test:watch` runs frontend tests interactively. Current tests cover
health connectivity, unavailable/error responses, and closed backend endpoints.

Playwright is isolated from the frontend unit-test installation. Start the
backend first; Playwright starts Vite automatically:

```sh
cd frontend/e2e
npm install
npx playwright install chromium
npm test
```

The browser smoke test exercises the actual React → Vite proxy → Spring Boot
connection. Commit the E2E lockfile generated by its first installation before
switching that package to `npm ci`. E2E is not yet included in CI.

GitHub Actions runs backend verification and frontend tests/builds on pushes
and pull requests once this folder is pushed to a GitHub repository.

## Optional Firestore emulator

Install the [Firebase CLI](https://firebase.google.com/docs/cli) and follow the
[Local Emulator Suite setup](https://firebase.google.com/docs/emulator-suite/install_and_configure).
Use Java 21 for the emulator as well. From the repository root:

```sh
firebase emulators:start --only firestore --project demo-roommate-expenses
```

- Firestore: `127.0.0.1:8081` (separate from Spring Boot on 8080).
- Emulator UI: **http://127.0.0.1:4000**.
- The `demo-` project is local-only; this command does not provision cloud resources.
- Data is ephemeral by default. Optional exports can be stored in ignored `.firebase-data/`.

When the backend persistence layer is implemented, configure its Firestore
Admin SDK with `GOOGLE_CLOUD_PROJECT=demo-roommate-expenses` and
`FIRESTORE_EMULATOR_HOST=127.0.0.1:8081`.
For deployed Firestore, remove the emulator variable and use Application Default
Credentials with a Cloud Run service account.

`firestore.rules` denies direct client reads/writes. Admin SDK calls bypass
these rules, so backend authorization must check household membership for every
read and write. Starting the emulator alone does not connect this scaffold to it.

## Docker and Cloud Run

From the repository root:

```sh
docker build -t roommate-expenses .
docker run --rm -p 8080:8080 roommate-expenses
```

Open **http://localhost:8080** for the compiled frontend and API in one container.
The runtime uses Java 21 and a non-root user; Spring listens on Cloud Run's `PORT`.
Only the app shell, its assets, and `GET /api/health` are public. All other
requests are denied until account authentication and authorization are built.
Spring's default generated development user is not an implemented account system.

Deployment is not configured or performed by this scaffold. Before deployment,
choose a GCP project and region, create Firestore, configure a least-privilege
runtime service account, publish the image to Artifact Registry, and deploy it
to Cloud Run. Store future OAuth credentials/tokens in Secret Manager. No service
account keys or OAuth tokens belong in the repository or container image.

## Repository layout

```text
backend/                 Spring Boot app, security, Maven wrapper, JUnit tests
frontend/                React app, Vite proxy, Vitest tests
frontend/e2e/            Separate Playwright package and browser smoke test
.github/workflows/       GitHub Actions build/test workflow
Dockerfile               Combined frontend/backend container
firebase.json            Local Firestore emulator configuration
firestore.rules          Deny direct client database access
```

## Minimal marketable features

1. **Accounts and households:** account creation/sign-in, household creation,
   invitation links or codes, and access restricted to household members.
2. **Expense splitting and balances:** description, amount, payer, participants,
   equal/custom splits, validation of totals, who owes whom, and expense history.
3. **Payment requests and email reminders:** outstanding shares, due dates,
   payment tracking, and Gmail reminders for unpaid requests.
4. **Payments and settlements:** simulated success marks a request paid and
   updates balances; simulated failure leaves it outstanding. No real transfers.
5. **Internal messaging:** expense/request threads with sender and timestamp.
6. **Spending summaries:** monthly household totals by category and personal share.

Prioritize accounts, expenses, requests, and mock payments before messaging
and spending summaries.

## Planned architecture and invariants

- **Strategy:** equal and custom expense-splitting implementations behind a shared contract.
- **Observer:** notifications react to expense/payment-request events.
- **Adapter:** email notification interface backed by Gmail REST/OAuth 2.0, with
  provider details kept outside domain logic so another provider can be added.
- React calls Spring Boot APIs; Spring Security establishes identity, while
  application services enforce household membership and resource ownership.
- Use integer cents or Java `BigDecimal` for money, never floating-point arithmetic.
  Equal splits distribute leftover cents deterministically; custom shares must
  add up exactly to the total. Final validation rules belong with the feature issue.
- Payment settlement and reminders must handle retries without double settlement
  or duplicate notifications. Gmail authorization is separate from app sign-in.

## AI-assisted development and proposed agent workflow

ChatGPT and Claude may help with implementation, debugging, and test generation.
Team members review generated code and expected results, manually test usability,
and review changes before merging. Use both blackbox requirements-based cases
and whitebox implementation-based cases, including rounding, invalid splits,
unauthorized household access, and repeated notifications.

The proposed first agentic task is the **backend expense-splitting component**,
not the whole app. Acceptance criteria: valid equal/custom splits preserve the
original total, invalid input is rejected, and tests cover cents/rounding and
edge cases.

The proposed `agent-ready` GitHub label would trigger implementation, testing,
and review agents on a branch/PR, with a bounded retry limit and a report when
unresolved. A human performs the final review before merge. This automation is
**not configured** here; the team will confirm the Claude Code/GitHub Actions
approach with the mentor before enabling it. The included workflow only builds
and tests the scaffold.

## Scaffold verification

In the setup environment, all three frontend tests and the production build
passed. Backend verification could not complete because the Spring Security and
validation dependencies were not cached and Maven Central was unreachable.
The Playwright smoke test, Firestore emulator, and Docker image have not been
run here. Run the commands above from a network-enabled environment to finish
verification before relying on the scaffold for feature development.

## Troubleshooting

- **`node:util` does not export `styleText` / Node 18:** switch to Node 22 using
  the commands above, then run `npm ci` in `frontend/` and restart `npm run dev`.
  Node 18 cannot run this frontend toolchain. Installation now rejects unsupported
  Node versions, and frontend commands check the version before loading Vite.

- **Backend unavailable:** check the backend terminal, `/api/health`, and proxy port.
- **Wrong Java version:** inspect `JAVA_HOME` and `./mvnw -version`; Maven may use
  a different JDK from `java` on your PATH.
- **Dependency downloads fail:** first-time setup requires access to npm and Maven
  Central. Retry installation from a network-enabled environment.
- **Port occupied:** stop the conflicting process or change the backend port and
  Vite proxy together. Firestore uses 8081 to avoid the backend's 8080.
- **403 on another API:** expected until authentication and the feature endpoints
  are implemented; the scaffold deliberately keeps application APIs closed.
