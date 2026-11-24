# Mock payment gateway

`PaymentGateway` defines a small backend contract that accepts a positive amount in integer cents and returns a `PaymentResult` with an attempt reference and status.

`MockPaymentGateway` is a local implementation for testing and development. It **does not charge money**, call Stripe or any other payment provider, access the network, or require credentials. It does not persist attempts or change payment-request or household balances.

Choose the outcome explicitly when creating the mock:

```java
PaymentGateway gateway = new MockPaymentGateway(PaymentStatus.SUCCEEDED);
PaymentResult result = gateway.attempt(1250);
```

Use `PaymentStatus.FAILED` to simulate a failed payment. The chosen outcome is returned for every valid attempt on that mock instance. References are predictable (`mock-attempt-1`, `mock-attempt-2`, etc.) and are unique only within a single instance; they are not durable transaction IDs. Invalid amounts (zero or negative cents) throw `IllegalArgumentException` without creating an attempt. A missing scenario is also rejected.

Run the JUnit tests from the `backend` directory:

```powershell
.\mvnw.cmd "-Dtest=MockPaymentGatewayTest" test
```
