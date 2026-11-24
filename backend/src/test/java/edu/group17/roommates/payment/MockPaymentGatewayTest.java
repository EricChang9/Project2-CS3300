package edu.group17.roommates.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockPaymentGatewayTest {
    @Test void returnsSuccessfulResult() {
        PaymentGateway gateway = new MockPaymentGateway(PaymentStatus.SUCCEEDED);

        PaymentResult result = gateway.attempt(1250);

        assertEquals("mock-attempt-1", result.attemptReference());
        assertEquals(PaymentStatus.SUCCEEDED, result.status());
    }

    @Test void returnsFailedResult() {
        PaymentGateway gateway = new MockPaymentGateway(PaymentStatus.FAILED);

        PaymentResult result = gateway.attempt(1250);

        assertEquals("mock-attempt-1", result.attemptReference());
        assertEquals(PaymentStatus.FAILED, result.status());
    }

    @Test void assignsPredictableAttemptReferences() {
        PaymentGateway gateway = new MockPaymentGateway(PaymentStatus.SUCCEEDED);

        assertEquals("mock-attempt-1", gateway.attempt(100).attemptReference());
        assertEquals("mock-attempt-2", gateway.attempt(200).attemptReference());
    }

    @Test void rejectsInvalidAmountsWithoutConsumingAnAttempt() {
        PaymentGateway gateway = new MockPaymentGateway(PaymentStatus.SUCCEEDED);

        assertThrows(IllegalArgumentException.class, () -> gateway.attempt(0));
        assertThrows(IllegalArgumentException.class, () -> gateway.attempt(-1));
        assertThrows(IllegalArgumentException.class, () -> gateway.attempt(Long.MIN_VALUE));
        assertEquals("mock-attempt-1", gateway.attempt(1).attemptReference());
    }

    @Test void requiresAnExplicitScenario() {
        assertThrows(IllegalArgumentException.class, () -> new MockPaymentGateway(null));
    }
}
