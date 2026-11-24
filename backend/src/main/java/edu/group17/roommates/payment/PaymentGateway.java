package edu.group17.roommates.payment;

public interface PaymentGateway {
    PaymentResult attempt(long amountCents);
}
