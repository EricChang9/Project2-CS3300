package edu.group17.roommates.payment;

import java.util.concurrent.atomic.AtomicLong;

public final class MockPaymentGateway implements PaymentGateway {
    private final PaymentStatus scenario;
    private final AtomicLong nextAttempt = new AtomicLong(1);

    public MockPaymentGateway(PaymentStatus scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("Payment scenario is required");
        }
        this.scenario = scenario;
    }

    @Override
    public PaymentResult attempt(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive cents");
        }
        return new PaymentResult("mock-attempt-" + nextAttempt.getAndIncrement(), scenario);
    }
}
