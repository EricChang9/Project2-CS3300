# Payment-request frontend

This UI depends on Issue #19, the payment-request creation and query API. Until that backend is merged, the UI can be tested with mocked responses but cannot create or list real requests.

The frontend expects these authenticated, household-scoped endpoints:

- `GET /api/households/{householdId}/payment-requests` returns `{ "items": [ ... ] }` with the requests visible to the current household member.
- `POST /api/households/{householdId}/payment-requests` accepts `{ "expenseId": "...", "debtorId": "...", "dueDate": "YYYY-MM-DD" }`. `dueDate` may be omitted. The backend derives the creditor, amount, and currency from the stored expense share.
- `GET /api/households/{householdId}/payment-requests/{requestId}` returns an authorized request detail.

Each returned request must include `id`, `expenseId`, `creditorId`, `debtorId`, `amountCents`, `currency`, `dueDate`, and `status` (`OUTSTANDING` or `PAID`). Returning `expenseDescription` allows the UI to display the expense name; otherwise it falls back to `expenseId`.

The backend remains responsible for authorization, validating unsettled shares and due dates, preventing duplicate active requests, and transitioning to paid status. The frontend hides obviously ineligible actions and handles 400, 403, 404, and 409 responses.

Run the UI tests with `cd frontend` followed by `npm test -- src/paymentRequests/PaymentRequests.test.jsx`. Run `npm test` and `npm run build` before merging.
