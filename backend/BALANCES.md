# Household balance API

`GET /api/households/{householdId}/balances` requires a Firebase ID token and current
membership in the requested household. A missing household or nonmember receives
the same `404` response as other household-scoped reads.

Balances are derived from each stored expense's `sharesCents`; the service does
not recompute splits from the expense amount or split type. For each expense, the
payer's own share is excluded. Each other participant's share is a directed
obligation from that participant to the payer. Opposing obligations between the
same two members are offset within the same currency, leaving at most one
direction for each pair. Currencies are never converted or added together.

The response contains:

- `debts`: net directed obligations with `debtorId`, `creditorId`, `currency`,
  and `amountCents`.
- `members`: every current household member. `byCurrency` contains their
  `owesCents`, `owedCents`, and `netBalanceCents` for each currency found in the
  household's expenses or settlements. Net is `owedCents - owesCents`; a
  positive amount means the member is owed money.
- `totals`: household debit and credit totals for each currency. The two totals
  are equal because each directed debt is counted once as a debit and once as a
  credit.

Amounts use integer minor units. The expense API currently accepts ISO 4217
currencies with two fraction digits. Balance arithmetic uses signed 64-bit
integers; an aggregate overflow returns `422` rather than a rounded result.

Successful payment records have a read seam in `SettlementStore`. The application
also supports `POST /api/households/{householdId}/settlements` with a Firebase ID
token and JSON containing `toUid`, `currency`, positive integer `amountCents`,
and a UUID `requestId`. The authenticated user is always the sender. Payments
must not exceed the sender's current net debt to the recipient, and repeated
requests with the same UUID and details create only one settlement. The feature
records a simulated payment; it does not transfer money. Payments are stored at
`households/{householdId}/settlements/{settlementId}` with `householdId`,
`fromUid`, `toUid`, `currency`, and positive integer `amountCents`. `fromUid` is
the debtor and `toUid` is the creditor. The calculator subtracts each successful
settlement from that direction's accumulated expense obligations, then nets
opposing directions. This lets later expenses update the household net without
rewriting old expenses. A settlement larger than the accumulated obligations in
its direction is treated as invalid stored data. Expenses and their original
shares are never changed.
