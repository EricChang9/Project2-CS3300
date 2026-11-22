# Project 2 black-box testing

This is the AI-assisted black-box contribution to Group 17's testing assignment.
Case design uses the proposal, issue acceptance criteria, documented HTTP DTOs,
and observable UI. Assertions exercise public inputs/outputs; they do not inspect
repositories, private functions, branch coverage, or database contents. The AI
agent had prior implementation context, so this is not claimed to be a blinded
experiment. Numeric expectations are explicit arithmetic examples, not copies
of implementation output.

The supplied `Testing.docx` template determines the report's section structure.
Generated PDF/DOCX and sanitized execution evidence are in `docs/testing/`.
The PDF is named `Group_17_Project2_Testing.pdf` as requested, but is explicitly
a **black-box contribution draft**, not a completed combined assignment: the
independent traditional cohort and white-box PR #59 must supply their results.

## Run

Prerequisites: Java 21, Node 22.12+ within Node 22, Python 3.10+, Unix/macOS/WSL,
Firebase CLI, frontend dependencies, and the isolated browser test dependencies.
From the repository root:

```sh
npm --prefix frontend ci
npm --prefix tests/blackbox ci
cd tests/blackbox
npx playwright install chromium
cd ../..
```

Start Firestore in one terminal if it is not already running:

```sh
firebase emulators:start --only firestore --project demo-roommate-expenses
```

Then run:

```sh
python3 tests/blackbox/run.py --emulator-host 127.0.0.1:8081
```

For a one-shot run using a separate emulator (also used in CI):

```sh
firebase emulators:exec --only firestore --config firebase.blackbox.json \
  --project demo-blackbox-suite \
  'python3 tests/blackbox/run.py --emulator-host 127.0.0.1:18082'
```

This runs 8 public-interface JUnit cases, 20 real HTTP integration cases, and 7
Chromium workflows. Tests launch their own backend on 18080 and web entry on
18081. Occupied ports fail safely instead of stopping somebody else's server.
`--api-port`/`--web-port` change those ports. `--skip-browser` is available for
diagnosis and records browser cases as **not run**, never passed.

Each run creates an exclusive `demo-bb-<random>` database namespace. Only that
namespace is cleared afterward; existing project data, development server,
accounts, and local `.env` files are untouched. No real Firebase accounts,
Stripe requests, Gmail messages, or paid cloud resources are created.

## Identity boundary and limitations

`BlackBoxTestServer` is on the Maven **test classpath only**. It replaces the
Google signing-key source with ephemeral RSA fixtures while retaining the real
Spring Security filters and production claim validation. The test-only web entry
injects an identity-provider adapter into the actual React app. App components,
HTTP transport, backend services, and Firestore persistence are real. The adapter
tests account-form behavior and session transitions, not Firebase's password
checking, signup service, key rotation, or revocation. Those remain explicitly
unexecuted cases in `cases.json`. The production launch/image contains neither
this Java test class nor the test web entry.

Runtime JWT files, logs, screenshots, and traces are ignored under `.artifacts/`;
the fixture token file is deleted after the run. Only sanitized case-level
evidence is committed. Evidence includes the product commit, UTC run interval,
versions, observed statuses, assertion errors, and measured execution duration.
Test failures are evidence to investigate, not automatic confirmed product bugs.
No production application behavior is changed to make this suite pass. The
separate assignment workflow runs for changes to this suite/report (or manual
dispatch); no branch protection or merge policy is changed by this PR.

The browser job pins Ubuntu 24.04, supported by the pinned Playwright release.
Actions use Node 24 runtimes while the app continues to use Node 22 from `.nvmrc`.
CI sets `BB_EVIDENCE_OUTPUT` to a fresh runner temporary file, so setup failures
cannot upload the checked-in local snapshot as current CI results. Local runs
still update `docs/testing/evidence/latest.json` by default; use
`--evidence-output /tmp/blackbox-evidence.json` to preserve that snapshot.

BBA15 compares `createdAt` instants at Firestore's documented microsecond
precision and all other fields exactly. The first Linux CI execution exposed
the original oracle's incorrect nanosecond-string requirement; this is harness
triage, excluded from product defect counts. See
[Firestore data types](https://firebase.google.com/docs/firestore/manage-data/data-types).

## Rebuild the report

```sh
python3 -m venv /tmp/fairshare-testing-docs
/tmp/fairshare-testing-docs/bin/pip install -r tests/blackbox/requirements-report.txt
/tmp/fairshare-testing-docs/bin/python tests/blackbox/build_report.py \
  --template /path/to/Testing.docx
```

The document generator reuses the supplied template's Word styles and its exact
section hierarchy. PDF and DOCX share one content model. PDF pagination/contents
are generated directly and checked with pypdf; it does not require Word to export.
When no template is provided, a standalone document with the same structure is
generated. Instructor template comments/placeholders are not carried forward.

## Traditional cohort and comparison

See `traditional/protocol.md` and its blank results CSV. Independent human design
and execution are required before reporting a traditional baseline or an AI
advantage. The AI-generated worksheet is not counted as human-authored testing.
Do not fabricate development time, defect counts, maintenance ratings, or
“AI-only” findings. The report distinguishes measured execution time from the
instrumented authoring wall-clock window (which includes setup/approval waits
and excludes initial planning), and records missing measurements as pending.

White-box work is tracked separately in PR #59. This suite does not claim to
fulfill that PR's unit/integration structural-coverage requirement.
