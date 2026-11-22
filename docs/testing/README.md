# Group 17 black-box testing contribution

- `Group_17_Project2_Testing.pdf`: assignment-formatted black-box contribution draft.
- `Group_17_Project2_Testing.docx`: editable companion using the supplied template styles.
- `evidence/latest.json`: sanitized actual unit/API/browser results and environment.
- `metrics.json`: derived traceability, counts, execution/authoring-window measurements.
- `defects.csv`: reproducible product findings; no production fixes are bundled.
- `harness-triage.json`: AI harness mistakes explicitly excluded from product findings.

Reproduction and scope: `../../tests/blackbox/README.md`.

The test runner intentionally exits nonzero when a true product oracle fails.
The recorded HTTP 403 status mismatches and fractional-cent truncation are not
changed into passes. A red test job is therefore expected until those product
issues are resolved in separate work. The CI artifact preserves sanitized results.

The PDF follows all supplied template sections but clearly marks traditional
measurements and white-box integration as pending. It is not a claim that the
whole group assignment is ready to submit. Fill the traditional results sheet,
confirm actual member contributions, integrate the white-box team's evidence
(PR #59), and regenerate/review the final combined document before submission.
