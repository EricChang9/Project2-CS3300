# Independent traditional black-box cohort

Status: **not executed or assigned by this agent**. Joseph Huang is the proposed
AI-assisted coordinator, subject to team confirmation; all human contributions
must be confirmed by the actual students before submission.

1. Assign 1–2 traditional testers and 1–2 AI-assisted testers. Use separate case
   design sheets. Traditional testers should not read the AI case inventory
   before freezing their own baseline; otherwise label the comparison as
   execution-only rather than independent test-design effectiveness.
2. Start a stopwatch before each design/setup/run/repair stage. Record active
   minutes separately from install/network/wait time and keep the same product
   commit, data fixtures, browser, and requirement scope across cohorts.
3. Derive cases from the proposal and public interface contract using equivalence
   partitions, boundary values, decision tables, and state transitions. Cover
   unit interfaces, HTTP integration, and observable browser workflows. Use
   manual operation or Selenium/Playwright without AI generation for this cohort.
4. Record each input, expected output **before execution**, actual output, status,
   author, duration, requirement IDs, and evidence location in `results.csv`.
   Keep unexpected outcomes; investigate whether they are product defects,
   harness errors, environmental failures, or ambiguous requirements.
5. Deduplicate confirmed bugs by root cause, not number of failed assertions.
   Record severity, reproduction, assignee, status, and resolution in the defect
   register. Do not use private source branches as black-box oracles.
6. Rate maintenance effort using actual changes: minutes to repair a locator,
   update a contract, and add a new boundary case. Explain the rating rather
   than assigning a self-reported “easy” score without evidence.
7. Merge cohort data into the assignment document. Compute cases/hour, unique
   confirmed defects/executed cases, requirements touched/eligible requirements,
   edge-case categories, and maintenance time. Report unavailable denominators
   and sample-size limits; an absent human baseline is not zero defects/time.

The existing AI tests use a simulated identity provider with real application
layers. Use the same limitation in the comparison or separately report any
live-Firebase test performed by a human; do not present the two scopes as equal.
